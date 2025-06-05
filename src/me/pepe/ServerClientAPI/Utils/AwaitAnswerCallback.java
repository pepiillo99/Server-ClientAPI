package me.pepe.ServerClientAPI.Utils;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public abstract class AwaitAnswerCallback {
	private int id;
	private long timeout = 5000;
	private long answerTime = 0;
	private Class<Packet> expectedPacketClass;
	public AwaitAnswerCallback(ClientConnection clientConnection) {
		this(clientConnection, 5000, null);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout) {
		this(clientConnection, timeout, null);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, Class<Packet> expectedPacketClass) {
		this(clientConnection, 5000, expectedPacketClass);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout, Class<Packet> expectedPacketClass) {
		this.id = clientConnection.getNextAwaitAnswerID();
		this.timeout = timeout;
		this.answerTime = System.currentTimeMillis();
		this.expectedPacketClass = expectedPacketClass;
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
	public void setExpectedPacket(Class<Packet> expectedPacketClass) {
		this.expectedPacketClass = expectedPacketClass;
	}
	public boolean isCorrectPacket(Packet packet) {
		return expectedPacketClass == null || expectedPacketClass.isInstance(packet);
	}
	public abstract void onAnswer(Packet packet);
	public abstract void onTimeOut();
}
