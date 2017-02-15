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

import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

/***********************************************************
 * The AI system for a TicTacToeGame. Most of the game control is handled by the
 * Server but the move selection is made here - either via user or an attached
 * AI system.
 ***********************************************************/
public class TicTacToeAI extends AbstractAI implements Serializable {

    private static final long serialVersionUID = -5293683841529261141L;

    public TicTacToeGame game; // The game that this AI system is playing

    private int wins = 0;
    private int losses = 0;
    private int ties = 0;

    //Start low (more random) and increase after each game to get less random
    private int heat = 2000000;
    private int heatMax = 10000;

    private int gamesPlayed = 0;

    public HashMap<String, BoardRecord> longTermMemory;
    private ArrayList<String> shortTermMemory;

    private String saveName;

    private long time;

    /*
    * 2 = Random
    * 1 = Smart
    * */
    private int aiType;

    public TicTacToeAI(String loadFrom, String saveTo, int type) {
        game = null;

        time = System.nanoTime();
        this.saveName = saveTo;

        this.aiType = type;

        longTermMemory = new HashMap<>();
        shortTermMemory = new ArrayList<String>();

        loadMemory(loadFrom);
    }

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

    private int getSmartMove(char[] boardConfig) {

        ArrayList<Integer> emptySpaces = getEmptySpaces(boardConfig);

        double bestScore = -1;
        String bestChoice = "";
        int move = 0;

        for (Integer emptySpace : emptySpaces) {
            int space = emptySpace;

//           System.out.println(space);

            char[] tempBoard = boardConfig.clone();
            tempBoard[space] = game.getPlayer() == 0 ? 'X' : 'O';
            String tempBoardString = boardToString(tempBoard);

            BoardRecord record = longTermMemory.get(tempBoardString);

            if (record != null) {
                float currentValue = (float) (record.getWins() - record.getLosses()) / (record.getLosses() + record.getWins() + record.getTies());

                if (currentValue > bestScore) {
                    bestScore = currentValue;
                    move = space;
                    bestChoice = tempBoardString;

                }

//                System.out.println(tempBoardString + " Wins: " + memory.getWins() + " Ties: " + memory.getTies() + " Losses: " + memory.getLosses() + " Score:" + currentValue);
            }
        }

        if (!Objects.equals(bestChoice, "")) {
            shortTermMemory.add(bestChoice);
            return move;

        } else {
            return getRandomMove(boardConfig);
        }
    }

    public synchronized String computeMove() {
        if (game == null) {
            System.err.println("CODE ERROR: AI is not attached to a game.");
            return "0,0";
        }

        char[] board = (char[]) game.getStateAsObject();

        if (aiType == 1) {
            if (((int) (Math.random() * heat)) == 2) {
                return "" + getRandomMove(board);
            } else {
                return "" + getSmartMove(board);
            }
        } else {
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

//        System.out.println(shortTermMemory);

        if (result == playerPiece) {
            wins++;
        } else if (result == 'T') {
            ties++;
        } else {
            losses++;
        }

        String s;

        for (String memory: shortTermMemory) {

            s = memory;

            BoardRecord boardRecord = longTermMemory.get(s);

            if (boardRecord != null) {
                //Win
                if (result == playerPiece) {
                    boardRecord.setWins(boardRecord.getWins() + 1);
//                    System.out.println(playerPiece + " " + result + " W");
                }
                //Tie
                else if (result == 'T') {
                    boardRecord.setTies(boardRecord.getTies() + 1);
//                    System.out.println(playerPiece + " " + result + " T");
                }
                //Lose
                else {
                    boardRecord.setLosses(boardRecord.getLosses() + 1);
//                    System.out.println(playerPiece + " " + result + " L");
                }

            } else {

                //Win
                if (result == playerPiece) {
                    boardRecord = new BoardRecord(1, 0, 0);
//                    System.out.println(playerPiece + " " + result + " W");
                }
                //Tie
                else if (result == 'T') {
                    boardRecord = new BoardRecord(0, 1, 0);
//                    System.out.println(playerPiece + " " + result + " T");
                }
                //Lose
                else {
                    boardRecord = new BoardRecord(0, 0, 1);
//                    System.out.println(playerPiece + " " + result + " L");
                }

                longTermMemory.put(s, boardRecord);
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

        if (heat < heatMax && gamesPlayed % 50 == 0) heat++;

        gamesPlayed++;
        game = null; // No longer playing a game though.
    }

    /**
     * Shutdown the AI - allowing it to save its learned experience
     **/
    @Override
    public synchronized void end() {
        System.out.println("Wins: " + wins + " Ties: " + ties + " Losses: " + losses + " Name: " + saveName + " Heat:" + heat);
        saveMemory(this.saveName);

        System.out.println("Time: " + (System.nanoTime() - time));

        System.out.println("\n\n");
        for (String key : longTermMemory.keySet()) {
            System.out.println(this.saveName);
            System.out.println(key + " " + longTermMemory.get(key).toString());
        }
        System.out.println("\n\n");
    }

    private void saveMemory(String fileLocation) {
        try {
            File varTmpDir = new File("data/" + fileLocation + ".ser");
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
            File varTmpDir = new File("data/" + fileLocation + ".ser");
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

        System.out.println("RECALLED MEMORIES from " + fileLocation + ": " + longTermMemory.toString());
    }

    public void updateLongTermMemory(String board, int wins, int ties, int losses) {
        BoardRecord stm = longTermMemory.get(board);
        stm.setWins(wins);
        stm.setTies(ties);
        stm.setLosses(losses);

        longTermMemory.replace(board, stm);
    }

    private void getLongTermMemory(String board) {
        BoardRecord stm = longTermMemory.get(board);
        System.out.println(stm.getWins());
        System.out.println(stm.getTies());
        System.out.println(stm.getLosses());
    }

    public static void main(String[] args) {
        TicTacToeAI ai = new TicTacToeAI("memoriesH", "memoriesH", 1);

        for (String key : ai.longTermMemory.keySet()) {
            System.out.println(key + " " + ai.longTermMemory.get(key).toString());
        }
    }
}