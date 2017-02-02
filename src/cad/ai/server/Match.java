/*******************
 * Christian A. Duncan
 * CSC350: Intelligent Systems
 * Spring 2017
 *
 * AI Game Server Project
 * This project is designed to support multiple game platforms to test
 * AI-based solutions.
 * See README file for more details.
 ********************/

package cad.ai.server;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.ArrayDeque;
import java.util.Deque;
import cad.ai.game.*;

/***********************************************************
 * A Match to play (at some point) in the tournament.
 * It consists of the two players (home and away) that
 * will play the game.  The game to play is provided at construction
 * time.
 * Yes, we could make these based on multiple players - but not needed... yet.
 ***********************************************************/
public class Match implements Callable<Integer> {
    public static enum State { NOT_STARTED, IN_PROGRESS, TIE, HOME_WIN, AWAY_WIN };
    public class Message {
	Player p;        // The player that sent it
	String message;  // The message
	Message(Player p, String message) { this.p = p; this.message = message; }
    };
    
    private Player home; // Player one
    private Player away; // Player two
    private Game game;   // Game to play
    private State state = State.NOT_STARTED;    // State of the match
    private Deque<Message> messages;             // Messages from Players to the Game
    private int errorCount = 0; // # consecutive errors (inv. moves)
    
    public Match(Player h, Player a, Game g) {
	this.home = h;
	this.away = a;
	this.game = g;
	this.state = State.NOT_STARTED;
	this.errorCount = 0;
	this.messages = new ArrayDeque<Message>();
    }

    public Integer call() {
	state = State.IN_PROGRESS;
	home.postMessage("@GAME:START:H:"+away.getName());
	away.postMessage("@GAME:START:A:"+home.getName());
	
	while (!game.isDone()) {
	    String state = game.getState(false);
	    if (state != null) {
		// The state has changed since last call
		home.postMessage("@GAME:STATE:" + state);
		away.postMessage("@GAME:STATE:" + state);
	    }
	    processMessages();
	    try { Thread.sleep(100); } catch (Exception e) { }  // Don't hog resources...
	}

	// Determine winner, and set state
	int winner = game.getWinner();
	char r;
	if (winner == 0) {
	    state = State.HOME_WIN; r = 'H';
	} else if (winner == 1) {
	    state = State.AWAY_WIN;; r = 'A';
	} else {
	    if (winner < -1) {
		// Something went amiss!
		debug("Game went awry!  Recording it as a TIE anyway.");
	    }
	    state = State.TIE; r = 'T';
	}

	// Inform the players
	home.postMessage("@GAME:RESULT:" + r);
	away.postMessage("@GAME:RESULT:" + r);
	
	// Free the players for another Match...
	home.clearMatch();
	away.clearMatch();
	return new Integer(0);
    }

    /** Accessor and mutator methods **/
    public State getState() { return state; }
    public Player getHome() { return home; }
    public Player getAway() { return away; }

    // Post a message from a Player to the Game.
    public synchronized void postMessage(Player originator, String message) {
	messages.addLast(new Message(originator, message));   // Store the message in the messages Queue.
    }

    // Process the messages that were received from Players.
    public synchronized void processMessages() {
	while (!messages.isEmpty()) {
	    Message m = messages.removeFirst();
	    if (m.p != home && m.p != away) {
		// Some other player sent it???
		m.p.postMessage("@GAME:ERROR:[Programming error] This player is not part of this game.");
	    } else {
		String response = game.processMove(m.p == home ? 0 : 1, m.message);
		m.p.postMessage("@GAME:" + response);
		if (response.startsWith("ERROR")) {
		    // There was an error with the move, have there been too many?
		    if (errorCount++ > 5) {
			// Player forfeits
			game.resign(m.p == home ? 0 : 1);
		    } else {
			// resend game state (as a reminder)
			m.p.postMessage("@GAME:STATE:" + game.getState(true));
		    }
		} else {
		    errorCount = 0;  // Reset it.
		}
	    }
	}
    }

    /**
     * Returns a "unique" header string - for debugging mainly.
     **/
    private String header() {
	return "Match (" + home.getID() + "/" + away.getID() + "): ";
    }

    synchronized private void debug(String message) {
	System.out.println("DEBUG: " + header() + message);
    }
}
