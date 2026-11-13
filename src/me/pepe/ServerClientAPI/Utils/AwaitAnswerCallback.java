package me.pepe.ServerClientAPI.Utils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public abstract class AwaitAnswerCallback {
	private int id;
	private long timeout = 5000;
	private volatile long answerTime = 0;
	private final AtomicBoolean completed = new AtomicBoolean();
	private Class<? extends Packet> expectedPacketClass;
	public AwaitAnswerCallback(ClientConnection clientConnection) {
		this(clientConnection, 5000, null);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout) {
		this(clientConnection, timeout, null);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, Class<? extends Packet> expectedPacketClass) {
		this(clientConnection, 5000, expectedPacketClass);
	}
	public AwaitAnswerCallback(ClientConnection clientConnection, long timeout, Class<? extends Packet> expectedPacketClass) {
		this.id = clientConnection.getNextAwaitAnswerID();
		this.timeout = timeout;
		this.expectedPacketClass = expectedPacketClass;
	}
	public int getID() {
		return id;
	}
	public long getTimeOut() {
		return timeout;
	}
	public boolean isTimeOut() {
		return answerTime != 0 && !completed.get() && System.nanoTime() - answerTime >= TimeUnit.MILLISECONDS.toNanos(timeout);
	}
    public boolean isWaiting() { return answerTime != 0 && !completed.get(); }
	public void setExpectedPacket(Class<? extends Packet> expectedPacketClass) {
		this.expectedPacketClass = expectedPacketClass;
	}
	public boolean isCorrectPacket(Packet packet) {
		return expectedPacketClass == null || expectedPacketClass.isInstance(packet);
	}
	public abstract void onAnswer(Packet packet);
	public abstract void onTimeOut();

	public void onFailure(Exception exception) {
		onTimeOut();
	}

	public void startWaiting() {
		answerTime = System.nanoTime();
	}

	public void complete(Packet packet) {
		if (completed.compareAndSet(false, true)) {
			onAnswer(packet);
		}
	}

	public void fail(Exception exception) {
		if (completed.compareAndSet(false, true)) {
			onFailure(exception);
		}
	}
}
