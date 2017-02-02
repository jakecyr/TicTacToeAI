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

package cad.ai.game;

import java.util.Random;

/***********************************************************
 * The AI system for a NimGame.
 *   Most of the game control is handled by the Server but
 *   the move selection is made here - either via user or an attached
 *   AI system.
 ***********************************************************/
public class NimAI extends AbstractAI {
    protected NimGame game;  // The game that this AI system is playing
    protected Random ran;
    
    public NimAI() {
	game = null;
	ran = new Random();
    }

    public void attachGame(Game g) {
	game = (NimGame) g;
    }
    
    /**
     * Returns the Move as a String "R,S"
     *    R=Row
     *    S=Sticks to take from that row
     **/
    public synchronized String computeMove() {
	if (game == null) {
	    System.err.println("CODE ERROR: AI is not attached to a game.");
	    return "0,0";
	}
	
	int[] rows = (int[]) game.getStateAsObject();

	// Just pick a random amount from a random row (that isn't zero)
	int r = ran.nextInt(rows.length);                // Pick a starting place
	while (rows[r] == 0) r = (r + 1) % rows.length;  // Find next row that is not zero (while loop if all 0s!)

	int take = ran.nextInt(rows[r]) + 1;
	return r + "," + take;
    }	
}
