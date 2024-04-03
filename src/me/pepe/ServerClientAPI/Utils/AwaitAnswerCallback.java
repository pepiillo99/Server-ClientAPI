package me.pepe.ServerClientAPI.Utils;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public abstract class AwaitAnswerCallback {
	private int id;
	private long timeout = 5000;
	private long answerTime = 0;
	public AwaitAnswerCallback(ClientConnection clientConnection) {
		this(clientConnection, 5000);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout) {
		this.id = clientConnection.getNextAwaitAnswerID();
		this.timeout = timeout;
		this.answerTime = System.currentTimeMillis();
	}
	public int getID() {
		return id;
	}
	public long getTimeOut() {
		return timeout;
	}
	public boolean isTimeOut() {
		return (answerTime + 5000) - System.currentTimeMillis() <= 0;
	}
	public abstract void onAnswer(Packet packet);
	public abstract void onTimeOut();
}
