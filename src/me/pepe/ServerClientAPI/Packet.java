package me.pepe.ServerClientAPI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Connections.ClientConnection;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.AwaitAnswerCallback;
import me.pepe.ServerClientAPI.Utils.PacketSentCallback;

public abstract class Packet {
	private int packetToClientType;
	private PacketSentCallback sentCallback = null;
	private boolean ignorable = false; // si el sistema ignora el envio del packet mientras se esta reconectando
	private AwaitAnswerCallback awaitAnswerCallback;
	private int pendentingAnswer = 0;
	public Packet(int packetToClientType) {
		this.packetToClientType = packetToClientType;
	}
	public int getPacketToClientType() {
		return packetToClientType;
	}
	private long current = 0;
	public long getCurrent() {
		return current;
	}
	public void setCurrent(long current) {
		this.current = current;
	}
	public boolean hasSentCallback() {
		return sentCallback != null;
	}
	public void setSentCallback(PacketSentCallback sendedCallback) {
		this.sentCallback = sendedCallback;
	}
	public PacketSentCallback getSentCallback() {
		return sentCallback;
	}
	public boolean isIgnorable() { // si es ignorable y no tiene un sent callback ni un answer callback
		return ignorable && !hasAwaitAnswerCallback() && !hasSentCallback() && pendentingAnswer != 0;
	}
	public void setIgnorable(boolean ignorable) {
		this.ignorable = ignorable;
	}
	public boolean hasAwaitAnswerCallback() {
		return awaitAnswerCallback != null;
	}
	public AwaitAnswerCallback getAwaitAnswerCallback() {
		return awaitAnswerCallback;
	}
	protected void setAwaitAnswerCallback(AwaitAnswerCallback awaitAnswerCallback) {
		this.awaitAnswerCallback = awaitAnswerCallback;
	}
	public void answer(ClientConnection connection, Packet packet) {
		if (hasPendentingAnswer()) {
			packet.setPendentingAnswer(pendentingAnswer);
			connection.sendPacket(packet);
		} else {
			System.out.println("El cliente no tiene awaitAnswer");
		}
	}
	public int getPendentingAnswer() {
		return pendentingAnswer;
	}
	public boolean hasPendentingAnswer() {
		return pendentingAnswer != 0;
	}
	public void setPendentingAnswer(int pendentingAnswer) {
		this.pendentingAnswer = pendentingAnswer;
	}
	public abstract Packet serialize(ByteArrayInputStream info) throws ReadPacketException;
	public abstract ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException;
}
