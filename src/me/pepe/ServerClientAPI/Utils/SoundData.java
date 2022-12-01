package me.pepe.ServerClientAPI.Utils;

public class SoundData {
	private float db;
	private float rms;
	public SoundData(float db, float rms) {
		super();
		this.db = db;
		this.rms = rms;
	}
	public float getDB() {
		return db;
	}
	public float getRMS() {
		return rms;
	}	
}