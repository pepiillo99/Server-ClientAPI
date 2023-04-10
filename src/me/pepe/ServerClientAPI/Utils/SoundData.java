package me.pepe.ServerClientAPI.Utils;

public class SoundData {
	private double db;
	private double rms;
	private int noise;
	public SoundData(double db2, double rms2, int noise) {
		this.db = db2;
		this.rms = rms2;
		this.noise = noise;
	}
	public double getDB() {
		return db;
	}
	public double getRMS() {
		return rms;
	}	
	public int getNoise() {
		return noise;
	}
}