/*
 Christian A. Duncan
 CSC350: Intelligent Systems
 Spring 2017

 AI Game Client
 This project is designed to link to a basic Game Server to test
 AI-based solutions.
 See README file for more details.
 */
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

    private int heat = 500;

    private int wins = 0;
    private int losses = 0;
    private int ties = 0;

    private HashMap<String, ShortTermMemory> longTermMemory;
    private Queue<String> records;

    private String saveName;

    /*
    * 2 = Random
    * 1 = Smart
    * */
    private int aiType;

    public TicTacToeAI(String fileName, int type) {
        game = null;

        this.saveName = fileName;
        this.aiType = type;

        longTermMemory = new HashMap<>();
        records = new LinkedList<String>();

        loadMemory(this.saveName);
    }

    private int getRandomMove(char[] boardConfig) {
        int random = (int) (Math.random() * getEmptySpaces(boardConfig).size());

        char currentPlayer = (game.getPlayer() == 0)? 'X':'O';

        char[] tempBoard = boardConfig.clone();

        tempBoard[getEmptySpaces(boardConfig).get(random)] = currentPlayer;

        records.add(boardToString(tempBoard));

        return getEmptySpaces(boardConfig).get(random);
    }

    private int getSmartMove(char[] boardConfig) {

        ArrayList<Integer> emptySpaces = getEmptySpaces(boardConfig);

        double bestScore = -1000000;
        String bestChoice = "";
        int move = 0;
        boolean found = false;

        for (Integer emptySpace : emptySpaces) {
            int space = emptySpace;

            char[] tempBoard = boardConfig.clone();
            tempBoard[space] = game.getPlayer() == 0 ? 'X' : 'O';
            String tempBoardString = boardToString(tempBoard);

            ShortTermMemory memory = longTermMemory.get(tempBoardString);

            if (memory != null) {
                double currentValue = ((float) (((memory.getWins() - memory.getLosses()) / (memory.getLosses() + memory.getWins() + memory.getTies())) + 1) / 2);
                double adjust = Math.random() * heat;

                currentValue *= 1000;
                currentValue += adjust;

                if (currentValue > bestScore) {
                    bestScore = currentValue;
                    move = space;
                    found = true;
                    bestChoice = tempBoardString;
                }
            }
        }

        if (found) {
            records.add(bestChoice);
//            System.out.println(bestScore);
            return move;

        } else {
//            System.out.println("Random");
            return getRandomMove(boardConfig);
        }
    }

    public synchronized String computeMove() {
        if (game == null) {
            System.err.println("CODE ERROR: AI is not attached to a game.");
            return "0,0";
        }

        char[] board = (char[]) game.getStateAsObject();

        if(aiType == 1){
            return "" + getSmartMove(board);
        }
        else{
            return "" + getRandomMove(board);
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

    private String boardToString(char[] board) {
        String boardString = "";

        for (char aBoard : board) {
            boardString += aBoard;
        }

        return boardString.replaceAll(" ", "_");
    }

    private void rememberGame(char result) {
        char playerPiece = game.getPlayer() == 0 ? 'H' : 'A';

        if (result == playerPiece) wins++;
        else if (result == 'T') ties++;
        else losses++;

        String s;

        for(int i = 0 ; i < records.size(); i++){
            s = records.remove().toString();

//            System.out.println(s);

            if (longTermMemory.containsKey(s)) {
                ShortTermMemory shortTermMemory = longTermMemory.get(s);

                //Win
                if (result == playerPiece) {
                    shortTermMemory.setWins(shortTermMemory.getWins() + 1);
                }
                //Tie
                else if (result == 'T') {
                    shortTermMemory.setTies(shortTermMemory.getTies() + 1);
                }
                //Lose
                else {
                    shortTermMemory.setLosses(shortTermMemory.getLosses() + 1);
                }

                longTermMemory.put(s, shortTermMemory);
            } else {
                ShortTermMemory shortTermMemory;

                //Win
                if (result == playerPiece) {
                    shortTermMemory = new ShortTermMemory(1, 0, 0);
                }
                //Tie
                else if (result == 'T') {
                    shortTermMemory = new ShortTermMemory(0, 1, 0);
                }
                //Lose
                else {
                    shortTermMemory = new ShortTermMemory(0, 0, 1);
                }

                longTermMemory.put(s, shortTermMemory);
            }
        }


    }

    /**
     * Inform AI who the winner is result is either (H)ome win, (A)way win,
     * (T)ie
     **/
    @Override
    public synchronized void postWinner(char result) {
        rememberGame(result);

//        if(heat > 0) heat = heat - 1;

//        System.out.println( game.getPlayer() == 0 ? 'X':'O' );

//        for(String s: longTermMemory.keySet()){
//            ShortTermMemory shortTermMemory1 = longTermMemory.get(s);
//            System.out.println(s + " " + shortTermMemory1.getWins() + " " + shortTermMemory1.getLosses() + " " + shortTermMemory1.getTies());
//        }

        game = null; // No longer playing a game though.

    }

    /**
     * Shutdown the AI - allowing it to save its learned experience
     **/
    @Override
    public synchronized void end() {
        System.out.println("Wins: " + wins + " Ties: " + ties + " Losses: " + losses + " Name: " + saveName);
        saveMemory(this.saveName);
    }

    private void saveMemory(String fileLocation) {
        try {
            File varTmpDir = new File(fileLocation + ".ser");
            if (!varTmpDir.exists()) {
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

    private void loadMemory(String fileLocation) {
        try {
            File varTmpDir = new File(fileLocation + ".ser");
            if (varTmpDir.exists()) {
                FileInputStream fileIn = new FileInputStream(varTmpDir);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                longTermMemory = (HashMap<String, ShortTermMemory>) in.readObject();
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