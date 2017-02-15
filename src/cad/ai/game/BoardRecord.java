package cad.ai.game;

import java.io.Serializable;

public class BoardRecord implements Serializable {

    private int wins = 0, ties = 0, losses = 0;

    public BoardRecord(int wins, int ties, int losses){
        this.wins = wins;
        this.ties = ties;
        this.losses = losses;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getTies() {
        return ties;
    }

    public void setTies(int ties) {
        this.ties = ties;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public String toString(){
        return this.wins + ", " + this.ties + ", " + this.losses;
    }

}
