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

import java.io.*;
import java.util.*;

/***********************************************************
 * The AI system for a TicTacToeGame. Most of the game control is handled by the
 * Server but the move selection is made here - either via user or an attached
 * AI system.
 ***********************************************************/
public class TicTacToeAI extends AbstractAI implements Serializable {

    private static final long serialVersionUID = -5293683841529261141L;

    public TicTacToeGame game; // The game that this AI system is playing

    private Random ran;

    private static final int reward = 1;

    private HashMap<String, Double> longTermMemory;
    private HashMap<String, Double> shortTermMemory;

    private String saveName;

    /*
    * 2 = Random
    * 1 = Smart
    * */
    private int aiType;

    public TicTacToeAI(String fileName, int type) {
        game = null;
        ran = new Random();

        this.saveName = fileName;
        this.aiType = type;

        longTermMemory = new HashMap<String, Double>();
        shortTermMemory = new HashMap<String, Double>();

        loadMemory(this.saveName);
    }

    public int getRandomMove(char[] boardConfig) {
        shortTermMemory.put(boardToString(boardConfig), 0.0);
        int random = (int) (Math.random() * getEmptySpaces(boardConfig).size());
        return getEmptySpaces(boardConfig).get(random);
    }

    public int getSmartMove(char[] boardConfig) {

        String boardString = boardToString(boardConfig);
        ArrayList<Integer> emptySpaces = getEmptySpaces(boardConfig);

        double bestScore = -100000;
        int move = 0;

        for (int i : emptySpaces) {
            char[] tempBoard = boardConfig.clone();
            tempBoard[i] = game.getPlayer() == 1 ? 'X' : 'O';
            String tempBoardString = boardToString(tempBoard);

            if (longTermMemory.containsKey(tempBoardString)) {
                double currentValue = longTermMemory.get(tempBoardString);
                if (currentValue > bestScore) {
                    bestScore = currentValue;
                    move = i;
                }
            }
            else{
                shortTermMemory.put(tempBoardString, 0.0);
            }
        }

        shortTermMemory.put(boardString, 0.0);

        if (bestScore != -100000) {
//            System.out.println("AI MOVE");
            return move;
        } else {
//            System.out.println("RANDOM MOVE");
            int random = (int) (Math.random() * emptySpaces.size());
            return emptySpaces.get(random);
        }
    }

    private ArrayList<Integer> getEmptySpaces(char[] board) {
        ArrayList<Integer> emptySpaces = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == ' ') {
                emptySpaces.add(i);
            }
        }
        return emptySpaces;
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

        return "" + getRandomMove(board);
    }

    private String boardToString(char[] board) {
        String boardString = "";

        for (int i = 0; i < board.length; i++) {
            boardString += board[i];
        }

        return boardString.replaceAll(" ", "_");
    }

    public void rememberGame(char result) {
        char playerPiece = game.getPlayer() == 1 ? 'H': 'A';

        for (String s : shortTermMemory.keySet()) {
            if (longTermMemory.containsKey(s)) {
                double tempScore = longTermMemory.get(s);

                //Win
                if (result == playerPiece) {
                    longTermMemory.replace(s, tempScore + 1);
                }
                //Tie
                else if (result == 'T') {

                }
                //Lose
                else {
                    longTermMemory.replace(s, tempScore - 1);
                }
            } else {
                //Win
                if (result == playerPiece) {
                    longTermMemory.put(s, 1.0);
                }
                //Tie
                else if (result == 'T') {

                }
                //Loss
                else {
                    longTermMemory.put(s, -1.0);
                }
            }
        }
    }

    /**
     * Inform AI who the winner is result is either (H)ome win, (A)way win,
     * (T)ie
     **/
    @Override
    public synchronized void postWinner(char result) {
        // This AI probably wants to store what it has learned
        // about this particular game.

        rememberGame(result);
        //System.out.println(longTermMemory.toString());
//        saveMemory(this.saveName);
        //System.out.println(longTermMemory.toString());
        game = null; // No longer playing a game though.
    }

    /**
     * Shutdown the AI - allowing it to save its learned experience
     **/
    @Override
    public synchronized void end() {
        // This AI probably wants to store (in a file) what
        // it has learned from playing all the games so far...

        saveMemory(this.saveName);
    }

    public void saveMemory(String fileLocation) {
        try {
            File varTmpDir = new File(fileLocation + ".ser");
            if(!varTmpDir.exists()) {
                varTmpDir.createNewFile();
            }
            FileOutputStream fileOut = new FileOutputStream(varTmpDir);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(longTermMemory);
            out.close();
            fileOut.close();
            System.out.println("Saved memories in " + fileLocation);
        } catch (IOException i) {
            i.printStackTrace();
        }

//        System.out.println(longTermMemory.toString());
    }

    public void loadMemory(String fileLocation) {
        try {
            File varTmpDir = new File(fileLocation + ".ser");
            if(varTmpDir.exists()) {
                FileInputStream fileIn = new FileInputStream(varTmpDir);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                longTermMemory = (HashMap<String, Double>) in.readObject();
                in.close();
                fileIn.close();
            }
        } catch (IOException i) {
            i.printStackTrace();
            return;
        } catch (ClassNotFoundException c) {
            System.out.println("File not found");
            c.printStackTrace();
            return;
        }

        System.out.println("RECALLED MEMORIES from " + fileLocation + ": " + longTermMemory.toString());
    }

}