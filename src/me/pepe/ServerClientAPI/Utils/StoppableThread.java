package me.pepe.ServerClientAPI.Utils;

public class StoppableThread extends Thread {
	private volatile boolean running = true;
	public boolean isRunning() {
		return running;
	}
	public void kill() {
		running = false;
		interrupt();
	}
}
