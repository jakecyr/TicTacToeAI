package cad.ai.game;

import java.io.Serializable;

public class GameRecord implements Serializable {
	private int wins;
	private int losses;
	private int ties;
	
	public GameRecord(int wins, int losses, int ties){
		this.wins = wins;
		this.losses = losses;
		this.ties = ties;
	}
	
	public void setWins(int wins){
		this.wins = wins;
	}
	
	public void setLosses(int losses){
		this.losses = losses;
	}
	
	public void setTies(int ties){
		this.ties = ties;
	}

	public int getWins() {
		return wins;
	}

	public int getLosses() {
		return losses;
	}

	public int getTies() {
		return ties;
	}
}
