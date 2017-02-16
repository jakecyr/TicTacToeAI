/*
 Christian A. Duncan
 CSC350: Intelligent Systems
 Spring 2017

 AI Game Client
 This project is designed to link to a basic Game Server to test
 AI-based solutions.
 See README file for more details.

 Edited By: Jake Cyr, Ryan Ek, and FanonX Rogers
 */
package cad.ai.game;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/***********************************************************
 * The AI system for a TicTacToeGame. Most of the game control is handled by the
 * Server but the move selection is made here - either via user or an attached
 * AI system.
 ***********************************************************/
public class TicTacToeAI extends AbstractAI implements Serializable {

    private static final long serialVersionUID = -5293683841529261141L;

    public TicTacToeGame game; // The game that this AI system is playing

    //Current game set counts
    private int wins = 0;
    private int losses = 0;
    private int ties = 0;

    private int heat = 10000; //Start low (more random) and increase after each game to get less random
    private int heatMax = 10000; //Maximum heat (keep <= 10,000 while training to allow some exploration
    private boolean useHeat = false; //Choose to use the heat value or not when deciding to choose a random or smart move.
    
    private int gamesPlayed = 0; //Count of the games played during the session

    public HashMap<String, BoardRecord> longTermMemory;
    private ArrayList<String> shortTermMemory;

    private String brainLocation;
    private int aiType;

    /*
    * AI Types
    * 1 = Smart
    * 2 = Random
    * */
    public TicTacToeAI(String brainFileLocation, int type) {
        game = null;

        this.brainLocation = brainFileLocation;
        this.aiType = type;

        //Initialize a new long term and short term memory
        longTermMemory = new HashMap<>();
        shortTermMemory = new ArrayList<String>();

        //Load the long term memories from the file specified
        loadMemory();
    }

    //Return a move based on the current board configuration
    public synchronized String computeMove() {
        if (game == null) {
            System.err.println("CODE ERROR: AI is not attached to a game.");
            return "0,0";
        }

        char[] board = (char[]) game.getStateAsObject();

        //Choose a smart move if that AI is set to "Smart" or if the random value equals 2
        if (aiType == 1) {
            if(useHeat && ((int) (Math.random() * heat)) == 2){
                return "" + getRandomMove(board);
            }
            else{
                return "" + getSmartMove(board);
            }
        } else {
            return "" + getRandomMove(board);
        }
    }

    //Return a random move based on the available spaces
    private int getRandomMove(char[] boardConfig) {
        ArrayList<Integer> emptySpaces = getEmptySpaces(boardConfig);

        int random = (int) (Math.random() * emptySpaces.size());
        int randomSpace = emptySpaces.get(random);
        char currentPlayer = (game.getPlayer() == 0) ? 'X' : 'O';
        char[] tempBoard = boardConfig.clone();

        tempBoard[randomSpace] = currentPlayer;
        shortTermMemory.add(boardToString(tempBoard));

        return randomSpace;
    }

    /*
    * Return the optimal move for the current empty spaces if the possible moves are in the long term memory.
    * Otherwise choose a random move.
    */
    private int getSmartMove(char[] boardConfig) {

        ArrayList<Integer> emptySpaces = getEmptySpaces(boardConfig);

        double bestScore = -1;
        String bestChoice = "";
        int move = 0;

        //Loop through each empty space on the board
        for (Integer emptySpace : emptySpaces) {
            int space = emptySpace;

            char[] tempBoard = new char[boardConfig.length];

            //Create a copy of the board array
            System.arraycopy(boardConfig, 0, tempBoard, 0, boardConfig.length);

            //Add the current player's piece to the current empty spot
            tempBoard[space] = game.getPlayer() == 0 ? 'X' : 'O';

            //Get a string from the possible move board
            String tempBoardString = boardToString(tempBoard);

            //Attempt to get the possible move board configuration data from the long term memory
            BoardRecord record = longTermMemory.get(tempBoardString);

            //Check if a record was found
            if (record != null) {

                //Calculate the score of the current move in question
                float currentValue = (float) (record.getWins() - record.getLosses()) / (record.getLosses() + record.getWins() + record.getTies());

                //If the new move is better than the current best move choose it
                if (currentValue > bestScore) {
                    bestScore = currentValue;
                    move = space;
                    bestChoice = tempBoardString;
                }
            }
        }

        if (!Objects.equals(bestChoice, "")) {
            shortTermMemory.add(bestChoice);
            return move;
        } else {
            return getRandomMove(boardConfig);
        }
    }

    /*
    * Input: the current board
    * Output: an ArrayList containing all of the empty spots on the board
    */
    private ArrayList<Integer> getEmptySpaces(char[] board) {
        ArrayList<Integer> emptySpaces = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == ' ') {
                emptySpaces.add(i);
            }
        }
        return emptySpaces;
    }

    //Link the AI to the current game
    public void attachGame(Game g) {
        game = (TicTacToeGame) g;
    }

    /*
    * Input: the current board
    * Output: an string representation of the referenced board state
    */
    private String boardToString(char[] board) {
        String boardString = "";
        for (char aBoard : board) boardString += aBoard;
        return boardString.replaceAll(" ", "_");
    }

    //Given the result of the game, give each move made either a reward or punishment
    private void rememberGame(char result) {

        char playerPiece = game.getPlayer() == 0 ? 'H' : 'A';

        if (result == playerPiece) {
            wins++;
        } else if (result == 'T') {
            ties++;
        } else {
            losses++;
        }

        // Loop through the short term memory and add each memory to the long term memory
        for (String memory : shortTermMemory) {

            BoardRecord boardRecord = longTermMemory.get(memory);

            //Board found in long term memory
            if (boardRecord != null) {
                //Win
                if (result == playerPiece) {
                    boardRecord.setWins(boardRecord.getWins() + 1);
                }
                //Tie
                else if (result == 'T') {
                    boardRecord.setTies(boardRecord.getTies() + 1);
                }
                //Lose
                else {
                    boardRecord.setLosses(boardRecord.getLosses() + 1);
                }
            }
            // New board configuration
            else {

                //Win
                if (result == playerPiece) {
                    boardRecord = new BoardRecord(1, 0, 0);
                }
                //Tie
                else if (result == 'T') {
                    boardRecord = new BoardRecord(0, 1, 0);
                }
                //Lose
                else {
                    boardRecord = new BoardRecord(0, 0, 1);
                }

                longTermMemory.put(memory, boardRecord);
            }
        }

        // Reset the short term memory after each game
        shortTermMemory = new ArrayList<>();
    }

    // Inform AI who the winner is result is either (H)ome win, (A)way win, (T)ie
    @Override
    public synchronized void postWinner(char result) {
        rememberGame(result);

        //Increase the heat (decrease randomness) as more games are played
        if (heat < heatMax && gamesPlayed % 50 == 0) heat++;

        gamesPlayed++;
        game = null; // No longer playing a game though.
    }

    // Shutdown the AI - allowing it to save its learned experience
    @Override
    public synchronized void end() {
        System.out.println("Wins: " + wins + " Ties: " + ties + " Losses: " + losses + " Name: " + brainLocation + " Heat:" + heat);
        saveMemory();
    }

    //Serialize the long term memory object to a file
    private void saveMemory() {
        try {
            File varTmpDir = new File("data/" + brainLocation + ".ser");
            if (!varTmpDir.exists()) {
                varTmpDir.createNewFile();
            }
            FileOutputStream fileOut = new FileOutputStream(varTmpDir);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(longTermMemory);
            out.close();
            fileOut.close();
            System.out.println("SAVED LONG TERM MEMORIES IN " + brainLocation);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    //Load the serialized memory from a file. If the file doesn't exist, create it.
    private void loadMemory() {
        try {
            File varTmpDir = new File("data/" + brainLocation + ".ser");
            if (varTmpDir.exists()) {
                FileInputStream fileIn = new FileInputStream(varTmpDir);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                longTermMemory = (HashMap<String, BoardRecord>) in.readObject();
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

        System.out.println("RECALLED LONG TERM MEMORIES FROM " + brainLocation + ": " + longTermMemory.toString());
    }

    //Update the counts of a specific long term memory
    public void updateLongTermMemory(String board, int wins, int ties, int losses) {
        BoardRecord stm = longTermMemory.get(board);
        stm.setWins(wins);
        stm.setTies(ties);
        stm.setLosses(losses);

        longTermMemory.replace(board, stm);
    }

    //Print the value in a long term memory
    private void getLongTermMemory(String board) {
        BoardRecord stm = longTermMemory.get(board);
        System.out.println(stm.getWins());
        System.out.println(stm.getTies());
        System.out.println(stm.getLosses());
    }
}