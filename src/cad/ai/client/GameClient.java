/*******************
 * Christian A. Duncan
 * CSC350: Intelligent Systems
 * Spring 2017
 *
 * AI Game Client
 * This project is designed to link to a basic Game Server to test
 * AI-based solutions.
 * See README file for more details.
 ********************/

package cad.ai.client;

import java.net.*;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.ArrayDeque;
import java.util.Deque;
import cad.ai.game.*;

/***********************************************************
 * The GameClient is the front-end text-based interface to the
 * Client-side of the Game Server.
 ***********************************************************/
public class GameClient {
    private BufferedReader userIn = null;  // User connection
    private String hostname;
    private int port = 1350;   // The default port that this client connects to...
    private String pname;      // The name of the player
    private int pid;           // The player's id in the game
    private int tid;           // The tournament's id (generally not useful if only one Tour running)
    private ExecutorService executor;    // Used to create new threads
    private Connection conn = null;
    private Game game = null;
    private AI ai = null;
    private int aiLevel = 0;
    private boolean done = false;
    
    /**
     * Constructor
     * @param hostname The name of the machine to connect to
     * @param port The port to connect to -1 means use default 1350
     * @param pname The name of the player
     * @param pid The id of the player if returning or -1 if a new player
     * @param tid The id of the tournament to join or -1 if just the first tourn available.
     * @param aiFlag Whether or not to create an AI for this client.
     * @param aiLevel If AI is true, level determines what version of AI to use.
     *
     * In general, the port, pid, and tid can just be -1.
     * The exception is if the player's GameClient crashed or lost connection and had to reconnect.
     * The pid can be used to connect back to the same player in the tournament.
     **/
    public GameClient(String hostname, int port, String pname, int pid, int tid,
		      boolean aiFlag, int aiLevel) {
	this.hostname = hostname;
	if (port >= 0) this.port = port;
	this.pname = pname;
	this.pid = pid;
	this.tid = tid;
	this.aiLevel = aiLevel;
	userIn = new BufferedReader(new InputStreamReader(System.in));
	executor = Executors.newCachedThreadPool();
	game = null;
	done = false;
	if (aiFlag) createAI(); else this.ai = null;
    }

    /**
     * Process the game (one frame)
     **/
    public synchronized void processGame() {
	if (game == null) return;  // No game yet.
	if (game.isPlayerTurn()) {
	    String move = game.getMove();
	    conn.postMessageLn(move);
	}
    }

    /**
     * Update the game state to the new game state provided
     * Synchronized in case Client is trying to display at the moment.
     **/
    public synchronized void updateGame(String state) {
	if (game == null)
	    System.err.println("Coding Error?  For some reason we aren't aware of playing any game right now.");
	else
	    game.updateState(state);   // Inform the game of the new game state
    }

    /**
     * Create a new game to play.
     * p - 0 if player is home and 1 if player is away.
     **/
    public synchronized void createNewGame(int p) {
	game = new TicTacToeGame(p, userIn, ai);
    }

    /**
     * Create a new AI to play this type of game
     **/
    public synchronized void createAI() {
	if (aiLevel <= 0) 
	    ai = new NimAI();   // To play Nim
	else if (aiLevel == 1)
	    ai = new TicTacToeAI();
	else {
	    System.err.println("WARNING: Unknown AI level.  Using default.");
	    ai = new TicTacToeAI();
	}
    }

    public synchronized void setDone(boolean flag) { done = flag; }
    
    /**
     * Start running the thread for this connection
     **/
    public void run() {
	try {
	    startConnection();
	    // Establish connection with the Game Server
	} catch (UnknownHostException e) {
	    System.err.println("Unknown host: " + hostname);
	    System.err.println("             " + e.getMessage());
	    System.exit(1);
	} catch (IOException e) {
	    System.err.println("IO Error: Error establishing communication with server.");
	    System.err.println("          " + e.getMessage());
	    System.exit(1);
	}

	while (!done) {
	    try {
		processGame();
		Thread.sleep(100);  // Sleep for a bit
	    } catch (InterruptedException e) {
	    }
	}

	System.out.println("Good-bye!");
	conn.close();
	executor.shutdown();
    }

    private class Connection implements Callable<Integer> {
	private Socket sock = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	private Deque<String> messages = null;
	
	public Connection() throws UnknownHostException, IOException {
	    this.sock = new Socket(hostname, port);
	    this.out = new PrintWriter(sock.getOutputStream(), true);
	    this.in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
	    this.messages = new ArrayDeque<String>();
	    initialHandshake();
	}
	
	/***
	 * The initial connection to the game server based on the parameters specified
	 * at creation time.
	 ***/
	private void initialHandshake() {
	    try {
		String response = null;  // The response to a query
		String pieces[] = null;  // The response in pieces
		// Create player
		if (pid == -1) {
		    out.println("@NEW PLAYER");    // New player
		} else {
		    out.println("@PLAYER:" + pid); // Returning player
		}

		// Set player name
		out.println("@NAME:" + pname);
		
		// Join the tournament
		if (tid < 0)
		    out.println("@TOUR:JOIN");
		else
		    out.println("@TOUR:JOIN:" + tid);
	    } catch (Exception e) {
		error("ABORTING: Communication error during handshake. " + e.getMessage());
		System.exit(1);
	    }
	}

	public Integer call() {
	    while (in != null && out != null) {
		try {
		    checkActive();
		    processInput();
		    transmitMessages();
		    Thread.sleep(10);  // Can't run super fast!
		}
		catch (InterruptedException e) { }
		catch (IOException e) {
		    error("I/O Exception: Ending connection.  " + e.getMessage());
		    this.close();
		}
	    }
	    return new Integer(0);
	}			    

	/**
	 * Determine if communication is still active
	 **/
	private long lastReceived = 0;
	private long lastPing = 0;
	private int pingCount = 0;
	private static final int MAX_PING = 10;
	private static final long MAX_QUIET = 200;
	private synchronized void checkActive() {
	    long time = System.currentTimeMillis();
	    if (time - lastReceived < MAX_QUIET) {
		// Nothing to do, we have received a message from server recently
		pingCount = 0;
		return;
	    }

	    if (time - lastPing > MAX_QUIET) {
		// We haven't transmitted a ping in a while
		if (pingCount < MAX_PING) {
		    // Try pinging the server again
		    postMessageLn("@PING");
		    lastPing = time;
		    pingCount++;
		} else {
		    // It has been far too long...
		    setDone(true);
		    this.close();
		}
	    }
	}
	
	/**
	 * Post a message to be transmitted to the Server (done next chance by Connection Thread)
	 **/
	public synchronized void postMessage(String message) {
	    messages.addLast(message);   // Store the message in the messages Queue.
	}
	
	/**
	 * Post a message to be transmitted to the Server (done next chance by Connection Thread)
	 * This also appends a new line to end of message.
	 **/
	public synchronized void postMessageLn(String message) {
	    messages.addLast(message + "\n");   // Store the message in the messages Queue.
	}
	
	/**
	 * Transmit (all) messages in the Queue.
	 **/
	private synchronized void transmitMessages() throws IOException {
	    if (out == null) return;  // No output buffer available
	    
	    while (!messages.isEmpty()) {
		String m = messages.removeFirst();
		out.print(m);
	    }
	    out.flush();
	}

	/**
	 * Get and process the input on the input stream
	 **/
	private void processInput() throws IOException {
	    if (in == null || !in.ready()) return;  // No input ready to process
	    
	    // We'll process only ONE action per frame - the rest are just QUEUED
	    // Of course, we could take all requests or just a few requests.
	    // This is to prevent some BOT from generating LOTS of action requests.
	    long time = System.currentTimeMillis();
	    String message = in.readLine();
	    lastReceived = time;  // For keeping connection alive...
	    
	    if (message == null) {
		// End of transmission
		this.close();
	    } else {
		// A message was provided.  Process this input message
		processInput(message);
	    }
	}

	/**
	 * Process the message provided.  Uses protocol described in ServerProtocol.txt
	 **/
	synchronized private void processInput(String message) {
	    try {
		String[] pieces = message.split(":", 5);
		String command = pieces[0].toUpperCase();
		switch (command) {
		case "@ERROR": processErrorMessage(pieces); break;
		case "@MESSAGE": processMessage(pieces); break;
		case "@PID": processPID(pieces); break;
		case "@TID": processTID(pieces); break;
		case "@REPORT": processReport(pieces); break;
		case "@GAME": processGameCommands(pieces); break;
		case "@TOUR": processTourCommands(pieces); break;
		case "@NAME": break; // Ignoring - for now...
		case "@PING": postMessageLn("@PONG"); break;
		case "@PONG": break;  // Ignore (already registered message received)
		default: error("Unrecognized command from server. " + message);
		}
	    } catch (Exception e) {
		error("Error processing command (" + message + "). " + e.getMessage());
	    }
	}

	synchronized private void processReport(String[] pieces) {
	    debug("Reports should not be sent to regular players.  Not sure why it was transmitted. Ignoring...");
	}

	synchronized private void processPID(String[] pieces) {
	    if (pieces.length < 2) {
		debug("PID was transmitted without a valid ID.");
	    } else {
		try { 
		    pid = Integer.parseInt(pieces[1]);
		    System.out.println("Player registered with ID=" + pid +
				       ".  Remember this in case you have to reconnect.");
		} catch (Exception e) {
		    debug("PID was not properly transmitted as an integer: " + pieces[1]);
		}
	    }
	}

	synchronized private void processTID(String[] pieces) {
	    if (pieces.length < 2) {
		debug("TID was transmitted without a valid ID.");
	    } else {
		try { 
		    tid = Integer.parseInt(pieces[1]);
		    System.out.println("Tournament registered with ID=" + tid +
				       ".  Remember this in case you have to reconnect.");
		} catch (Exception e) {
		    debug("TID was not properly transmitted as an integer: " + pieces[1]);
		}
	    }
	}

	synchronized private void processErrorMessage(String[] pieces) {
	    if (pieces.length < 2) {
		debug("Error Message was incorrectly transmitted by server.");
	    } else {
		display("ERROR: " + pieces[1]);
	    }
	}

	synchronized private void processMessage(String[] pieces) {
	    if (pieces.length < 2) {
		debug("Message was incorrectly transmitted by server.");
	    } else {
		display(pieces[1]);
	    }
	}

	synchronized private void processTourCommands(String[] pieces) {
	    if (pieces.length < 2) {
		debug("No tour subcommand submitted...");
		return;
	    }
	    String command = pieces[1];
	    switch(command) {
	    case "END": processTourEnd(); break;
	    default: debug("Unrecognized tour command transmitted: " + command);
	    }
	}

	/**
	 * The Tournament has ended (according to message received).
	 * Rather than join another one, we'll just quit
	 **/
	synchronized private void processTourEnd() {
	    display("The tournament has ended.");
	    setDone(true);
	}
	
	synchronized private void processGameCommands(String[] pieces) {
	    if (pieces.length < 2) {
		debug("No game subcommand submitted...");
		return;
	    }
	    String command = pieces[1];
	    switch(command) {
	    case "START": processGameStart(pieces); break;
	    case "STATE": processGameState(pieces); break;
	    case "ERROR": processGameErrorMessage(pieces); break;
	    case "MESSAGE": processGameMessage(pieces); break;
	    case "RESULT": processGameResult(pieces); break;
	    default: debug("Unrecognized game command transmitted: " + command);
	    }
	}

	synchronized private void processGameStart(String[] pieces) {
	    if (pieces.length < 4) {
		debug("Game Start message was incorrectly transmitted!");
		return;
	    }
	    
	    // A new game is to start
	    int p = (pieces[2].charAt(0) == 'H' ? 0 : 1);  // Get player's role (H or A)
	    System.out.println("A new game has started.  You are " +
			       ((p == 0) ? "Home" : "Away") +
			       ". Your opponent is " + pieces[3] + ".");
	    createNewGame(p);
	}
	
	synchronized private void processGameState(String[] pieces) {
	    if (pieces.length < 3)
		debug("No game state information was transmitted!");
	    else
		updateGame(pieces[2]);
	}
	
	synchronized private void processGameErrorMessage(String[] pieces) {
	    if (pieces.length < 3) {
		debug("Game Error Message was incorrectly transmitted by server.");
	    } else {
		display("GAME ERROR: " + pieces[2]);
	    }
	}

	synchronized private void processGameMessage(String[] pieces) {
	    if (pieces.length < 3) {
		debug("Game Message was incorrectly transmitted by server.");
	    } else {
		display(pieces[2]);
	    }
	}

	synchronized private void processGameResult(String[] pieces) {
	    if (pieces.length < 3) {
		debug("Game Result was incorrectly transmitted by server.");
	    } else {
		char result = pieces[2].charAt(0);
		game.postWinner(result);
		game = null;  // No longer need to store this game
	    }
	}
	
        // Close the connection (can also be used to stop the thread)
	public synchronized void close() {
	    try {
		if (in != null) in.close();
		if (out != null) out.close();
		if (sock != null) sock.close();
	    } catch (IOException e) {
		error("Error trying to close client connection: " + e.getMessage());
	    } finally {
		in = null; out = null; sock = null;
		setDone(true);  // Connection done, nothing left to do...
	    }
	}

	// For displaying debug, error, and regular messages
	private String header() { return "GameClient.Connection: "; }
	private void error(String message) { System.err.println(header() + message); }
	private void debug(String message) { System.err.println(header() + "DEBUG: " + message); }
	private void display(String message) {
	    System.out.println("Server: " + message);
	}
    }
    
    /***
     * Start a basic connection running that reads game state updates
     * from the Game Server
     ***/
    private void startConnection() throws UnknownHostException, IOException {
	conn = new Connection();
	FutureTask<Integer> task = new FutureTask<Integer>(conn);
	executor.execute(task);
    }

    /**
     * The main entry point.
     **/
    public static void main(String[] args) {
	// Defaults to use
	String hostname = "localhost";
	int port = -1;
	String name = "???";
	int pid = -1;
	int tid = -1;
	boolean ai = true;
	int aiLevel = 1;
	
	// Parse the arguments
	for (String arg: args) {
	    try {
		String[] params = arg.split("=",2);
		switch (params[0]) {
		case "--help": printUsage(null); break;
		case "--host": hostname = params[1]; break;
		case "--port": port = Integer.parseInt(params[1]); break;
		case "--name": name = params[1]; break;
		case "--pid": pid = Integer.parseInt(params[1]); break;
		case "--tid": tid = Integer.parseInt(params[1]); break;
		case "+ai": ai = true; break;
		case "-ai": ai = false; break;
		case "--ai":
		    if (params[1].equals("true")) ai=true;
		    else if (params[1].equals("false")) ai=false;
		    else printUsage("AI setting must be true or false");
		    break;
		case "--level": aiLevel = Integer.parseInt(params[1]); break;
		default:
		    printUsage("Unrecognized parameter: " + arg);
		}
	    } catch (Exception e) {
		printUsage("Error processing parameter: " + arg);
	    }
	}	    

	GameClient c = new GameClient(hostname, port, name, pid, tid,
				      ai, aiLevel);
	c.run();
    }

    /**
     * Print Usage message and exit
     **/
    public static void printUsage(String message) {
	System.err.println("Usage: java cad.ai.client.GameClient [params]");
	System.err.println("       Where params are:");
	System.err.println("         --help   Print this help message.");
	System.err.println("         --host=hostname");
	System.err.println("         --port=integer The port to connect to");
	System.err.println("         --name=playerName");
	System.err.println("         --pid=playerID");
	System.err.println("         --tid=tournamentID");
	System.err.println("         --ai=true/false [default=true]");
	System.err.println("         [+/-]ai  -- Use or don't use AI");
	System.err.println("         --level=X   The level of AI to use 0, 1, ...  (0=NimAi, 1=TTTAI) [default=1]");
	if (message != null) System.err.println("       " + message);
	System.exit(1);
    }       
}
