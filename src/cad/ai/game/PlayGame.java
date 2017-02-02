/*******************
 * Christian A. Duncan
 * CSC350: Intelligent Systems
 * Spring 2017
 *
 * AI Game Interface
 * This project is designed to support a simple direct interaction of a 2-player turn-based game.
 * See README file for more details.
 ********************/

package cad.ai.game;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/***********************************************************
 * The PlayGame class is designed to allow two players to player against each
 * other off-line. At start up, players can decide if home is AI or human and
 * the same for the away.
 *
 * Currently, only NIM is supported but options to choose a game will be added
 * in a future release.
 ***********************************************************/
public class PlayGame {
	private BufferedReader userIn = null; // Access to user input
	private Game game[];
	private Game serverGame = null;
	private AI ai[];

	private static enum GameType {
		NIM, TTT
	};

	private GameType gameType;
	private int numGames;
	private int verbose;

	/**
	 * Constructor
	 * 
	 * @param homeAI
	 *            0 if home is human, 1-3 if home is AI (of diff types)
	 * @param awayAI
	 *            0 if away is human, 1-3 if away is AI (of diff types)
	 * @param gameType
	 *            - what type of game to play.
	 * @param numGames
	 *            - the number of games to play.
	 * @param verbose
	 *            - how much to output [0 = quite, >0 = noisier]
	 **/
	public PlayGame(int homeAI, int awayAI, GameType gameType, int numGames, int verbose) {
		userIn = new BufferedReader(new InputStreamReader(System.in)); // To
																		// access
																		// user
																		// input
																		// stream
		game = new Game[2]; // A copy of the game for each player.
		ai = new AI[2]; // A spot for ai for each player.
		this.gameType = gameType;
		this.numGames = numGames;
		this.verbose = verbose;

		// Create the game and AI based on type
		// Create the AI based on type
		switch (gameType) {
		case TTT:
			switch (homeAI) {
			case 0:
				ai[0] = null;
				break;
			case 1:
				ai[0] = new TicTacToeAI();
				break; // Edit these for
			case 2:
				ai[0] = new TicTacToeAI();
				break; // different params
			case 3:
				ai[0] = new TicTacToeAI();
				break; // if you want...
			}
			switch (awayAI) {
			case 0:
				ai[1] = null;
				break;
			case 1:
				ai[1] = new TicTacToeAI();
				break; // Edit these as well...
			case 2:
				ai[1] = new TicTacToeAI();
				break;
			case 3:
				ai[1] = new TicTacToeAI();
				break;
			}
			break;
		case NIM:
			if (homeAI > 0)
				ai[0] = new NimAI();
			else
				ai[0] = null;
			if (awayAI > 0)
				ai[1] = new NimAI();
			else
				ai[1] = null;
			break;
		}
	}

	/**
	 * Start playing the game
	 **/
	public void run() {
		// Play multiple games...
		for (int i = 0; i < numGames; i++) {
			createGame();
			playGame();
		}

		// Let both AI's know we are done - so it can save state...
		for (int i = 0; i < ai.length; i++)
			if (ai[i] != null)
				ai[i].end();

		System.out.println("Good-bye!");
	}

	/**
	 * Create a new "game" based on Game Type
	 **/
	public void createGame() {
		switch (gameType) {
		case TTT:
			for (int p = 0; p < 2; p++)
				game[p] = new TicTacToeGame(p, userIn, ai[p], false, verbose);
			serverGame = new TicTacToeGame(-1, userIn, null, true, verbose);
			break;
		case NIM:
			for (int p = 0; p < 2; p++)
				game[p] = new NimGame(p, userIn, ai[p], false);
			serverGame = new NimGame(-1, userIn, null, true);
			break;
		}
	}

	/**
	 * Play the current game
	 **/
	private void playGame() {
		while (!serverGame.isDone()) {
			String state = serverGame.getState(true); // Get the state
			int turn = serverGame.getTurn(); // Whose turn is it
			if (turn >= 0 && turn <= 1) {
				// Get the move based on current game state
				game[turn].updateState(state);
				String move = game[turn].getMove();
				processInput(move, turn);
			}
		}

		// Display the board one last time
		if (verbose > 0)
			serverGame.displayState();

		// Let the games both know the winner...
		int winner = serverGame.getWinner();
		if (verbose > 0) {
			if (winner == 0) {
				System.out.println("Home won.");
			} else if (winner == 1) {
				System.out.println("Away won.");
			} else {
				System.out.println("It was a tie."); // Not possible in NIM.
			}
		}

		char r = winner == 0 ? 'H' : winner == 1 ? 'A' : 'T';
		game[0].postWinner(r);
		game[1].postWinner(r);
	}

	/**
	 * Process the message provided. Uses protocol described in
	 * ServerProtocol.txt
	 * 
	 * @param message
	 *            The message to process
	 * @param p
	 *            The player that sent it
	 **/
	synchronized private void processInput(String message, int p) {
		try {
			String[] pieces = message.split(":", 5);
			String command = pieces[0].toUpperCase();
			switch (command) {
			case "@ERROR":
				processErrorMessage(pieces, p);
				break;
			case "@MESSAGE":
				processMessage(pieces, p);
				break;
			case "@GAME":
				processGameCommands(pieces, p);
				break;
			default:
				error("Unrecognized command from server. " + message);
			}
		} catch (Exception e) {
			error("Error processing command (" + message + "). " + e.getMessage());
		}
	}

	synchronized private void processErrorMessage(String[] pieces, int p) {
		if (pieces.length < 2) {
			debug("Error Message was incorrectly transmitted.");
		} else {
			display("ERROR: " + pieces[1]);
		}
	}

	synchronized private void processMessage(String[] pieces, int p) {
		if (pieces.length < 2) {
			debug("Message was incorrectly transmitted.");
		} else {
			if (verbose > 0)
				display(pieces[1]);
		}
	}

	synchronized private void processGameCommands(String[] pieces, int p) {
		if (pieces.length < 2) {
			debug("Error.  No game subcommand submitted...");
			return;
		}
		String command = pieces[1];
		switch (command) {
		case "START":
			processGameStart(pieces, p);
			break;
		case "STATE":
			processGameState(pieces, p);
			break;
		case "MOVE":
			processGameMove(pieces, p);
			break;
		case "ERROR":
			processGameErrorMessage(pieces, p);
			break;
		case "MESSAGE":
			processGameMessage(pieces, p);
			break;
		case "RESULT":
			processGameResult(pieces, p);
			break;
		default:
			debug("Unrecognized game command transmitted: " + command);
		}
	}

	synchronized private void processGameStart(String[] pieces, int p) {
		debug("Error.  This should not need to be sent in PlayGame matches.");
	}

	synchronized private void processGameState(String[] pieces, int p) {
		debug("Hmm, this should not be transmitted as input to process.  Ignoring...");
	}

	synchronized private void processGameMove(String[] pieces, int p) {
		if (pieces.length < 3)
			debug("No game move information was transmitted!");
		else {
			String res = serverGame.processMove(p, pieces[2]);
			if (verbose > 0) {
				String message = res + (p == 0 ? "[Home]" : "[Away]");
				System.out.println(message);
			}
		}
	}

	synchronized private void processGameErrorMessage(String[] pieces, int p) {
		if (pieces.length < 3) {
			debug("Game Error Message was incorrectly transmitted.");
		} else {
			display("GAME ERROR: " + pieces[2]);
		}
	}

	synchronized private void processGameMessage(String[] pieces, int p) {
		if (pieces.length < 3) {
			debug("Game Message was incorrectly transmitted.");
		} else {
			if (verbose > 0)
				display(pieces[2]);
		}
	}

	synchronized private void processGameResult(String[] pieces, int p) {
		debug("Hmm, this should not be transmitted either.");
	}

	// For displaying debug, error, and regular messages
	private void error(String message) {
		System.err.println("ERROR: " + message);
	}

	private void debug(String message) {
		System.err.println("DEBUG: " + message);
	}

	private void display(String message) {
		System.out.println(message);
	}

	/**
	 * The main entry point.
	 **/
	public static void main(String[] args) {
		// Defaults to use
		int homeAI = 1; // 0=Human, 1-3=AI (various levels)
		int awayAI = 1; // ... same ...
		GameType gameType = GameType.TTT; // Default it TTT
		int repeat = 1; // Number of games to play
		int verbose = 1; // How "noisy" to be

		// Parse the arguments
		for (String arg : args) {
			try {
				String[] params = arg.split("=", 2);
				switch (params[0]) {
				case "--help":
					printUsage(null); // just print the Help message and exit
					break;
				case "--home":
					switch (params[1]) {
					case "ai":
					case "1":
						homeAI = 1;
						break;
					case "2":
						homeAI = 2;
						break;
					case "3":
						homeAI = 3;
						break;
					case "human":
					case "0":
						homeAI = 0;
						break;
					default:
						printUsage("Unrecognized option to --home");
					}
					break;
				case "--away":
					switch (params[1]) {
					case "ai":
					case "1":
						awayAI = 1;
						break;
					case "2":
						awayAI = 2;
						break;
					case "3":
						awayAI = 3;
						break;
					case "human":
					case "0":
						awayAI = 0;
						break;
					default:
						printUsage("Unrecognized option to --away");
					}
					break;
				case "--game":
					switch (params[1].toUpperCase()) {
					case "NIM":
						gameType = GameType.NIM;
						break; // Silly sanity check...
					case "TTT":
						gameType = GameType.TTT;
						break;
					default:
						printUsage("Unrecognized game option: " + params[1]);
					}
					break;
				case "--repeat":
					repeat = Integer.parseInt(params[1]);
					break;
				case "--verbose":
					verbose = Integer.parseInt(params[1]);
					break;
				default:
					printUsage("Unrecognized parameter: " + arg);
				}
			} catch (Exception e) {
				printUsage("Error processing parameter: " + arg);
			}
		}

		PlayGame c = new PlayGame(homeAI, awayAI, gameType, repeat, verbose);
		c.run();
	}

	/**
	 * Print Usage message and exit
	 **/
	public static void printUsage(String message) {
		System.err.println("Usage: java cad.ai.game.PlayGame [params]");
		System.err.println("       Where params are:");
		System.err.println("         --help                -- Print this usage message");
		System.err.println("         --home=ai/human/0-3   -- Home is ai or human (default is ai).");
		System.err.println("                                  #s can also be used to identify various AIs to use");
		System.err.println("         --away=ai/human/0-3   -- Away is ai or human (default is ai).");
		System.err.println("         --game=XXX            -- Can be either NIM or TTT (Tic-Tac-Toe).");
		System.err.println("         --repeat=X            -- Number of games to play (default 1).");
		System.err.println("         --verbose=X           -- 0=quiet, >0=Output more stuff.");
		if (message != null)
			System.err.println("       " + message);
		System.exit(1);
	}
}
