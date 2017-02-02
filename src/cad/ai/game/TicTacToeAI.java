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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/***********************************************************
 * The AI system for a TicTacToeGame. Most of the game control is handled by the
 * Server but the move selection is made here - either via user or an attached
 * AI system.
 ***********************************************************/
public class TicTacToeAI extends AbstractAI implements Serializable {

	private static final long serialVersionUID = -5293683841529261141L;
	public TicTacToeGame game; // The game that this AI system is playing
	Random ran;
	
	private Queue shortTermMemory;
	private HashMap<String, GameRecord> longTermMemory;
	
	private String saveLocation;

	public TicTacToeAI() {
		game = null;
		ran = new Random();
		saveLocation = "memories.ser";
		
		shortTermMemory = new LinkedList<GameRecord>();
		longTermMemory = new HashMap<String, GameRecord>();
	}

	public void attachGame(Game g) {
		game = (TicTacToeGame) g;
	}

	/**
	 * Returns the Move as a String "R,S" R=Row S=Sticks to take from that row
	 **/
	public synchronized String computeMove() {
		if (game == null) {
			System.err.println("CODE ERROR: AI is not attached to a game.");
			return "0,0";
		}

		char[] board = (char[]) game.getStateAsObject();

		// First see how many open slots there are
		int openSlots = 0;
		int i = 0;
		for (i = 0; i < board.length; i++)
			if (board[i] == ' ')
				openSlots++;

		// Now pick a random open slot
		int s = ran.nextInt(openSlots);

		// And get the proper row
		i = 0;
		while (s >= 0) {
			if (board[i] == ' ')
				s--; // One more open slot down
			i++;
		}

		// The position to use is the previous position
		int pos = i - 1;
		
		shortTermMemory.add(boardToString(board));

		return "" + pos;
	}
	
	private String boardToString(char[] board){
		String boardString = "";
		
		for(int i = 0; i < board.length; i++){
			boardString += board[i];
		}
		
		return boardString.replaceAll(" ", "_");
	}

	/**
	 * Inform AI who the winner is result is either (H)ome win, (A)way win,
	 * (T)ie
	 **/
	@Override
	public synchronized void postWinner(char result) {
		// This AI probably wants to store what it has learned
		// about this particular game.
		game = null; // No longer playing a game though.
		
		for(int i = 0; i < shortTermMemory.size(); i++){
			longTermMemory.put((String) shortTermMemory.remove(), new GameRecord(0,0,0));
		}
		
		System.out.println(longTermMemory.toString());
		
		saveMemory(saveLocation);
	}

	/**
	 * Shutdown the AI - allowing it to save its learned experience
	 **/
	@Override
	public synchronized void end() {
		// This AI probably wants to store (in a file) what
		// it has learned from playing all the games so far...
	}
	
	
	public void saveMemory(String fileLocation) {
        try {
            FileOutputStream fileOut = new FileOutputStream(fileLocation);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(longTermMemory);
            out.close();
            fileOut.close();
            System.out.printf("Saved memories in " + fileLocation);
        } catch (IOException i) {
            i.printStackTrace();
        }

        System.out.println(longTermMemory.toString());
    }

    public void loadMemory(String fileLocation) {
        try {
            FileInputStream fileIn = new FileInputStream(fileLocation);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            longTermMemory = (HashMap<String, GameRecord>) in.readObject();
            in.close();
            fileIn.close();
        } catch (IOException i) {
            i.printStackTrace();
            return;
        } catch (ClassNotFoundException c) {
            System.out.println("Employee class not found");
            c.printStackTrace();
            return;
        }

        System.out.println("RECALLED MEMORIES from " + fileLocation + ": " + longTermMemory.toString());
    }

}
