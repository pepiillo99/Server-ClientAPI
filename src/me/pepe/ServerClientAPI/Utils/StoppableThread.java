package me.pepe.ServerClientAPI.Utils;

public class StoppableThread extends Thread {
	private boolean running = true;
	public boolean isRunning() {
		return running;
	}
	public void kill() {
		running = false;
		interrupt();
	}
}