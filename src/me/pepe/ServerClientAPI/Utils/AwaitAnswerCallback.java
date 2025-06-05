package me.pepe.ServerClientAPI.Utils;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public abstract class AwaitAnswerCallback {
	private int id;
	private long timeout = 5000;
	private long answerTime = 0;
	private final Class<Packet> expectedClass;
	public AwaitAnswerCallback(ClientConnection clientConnection) {
		this(clientConnection, 5000, null);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout) {
		this(clientConnection, timeout, null);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, Class<Packet> expectedPacket) {
		this(clientConnection, 5000, expectedPacket);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout, Class<Packet> expectedPacket) {
		this.id = clientConnection.getNextAwaitAnswerID();
		this.timeout = timeout;
		this.answerTime = System.currentTimeMillis();
		this.expectedClass = expectedPacket;
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
	public boolean isCorrectPacket(Packet packet) {
		return expectedClass == null || expectedClass.isInstance(packet);
	}
	public abstract void onAnswer(Packet packet);
	public abstract void onTimeOut();
}
