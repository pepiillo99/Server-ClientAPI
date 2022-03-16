package me.pepe.ServerClientAPI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketSentCallback;

public abstract class Packet {
	private int packetToClientType;
	private PacketSentCallback sentCallback = null;
	private boolean ignorable = false; // si el sistema ignora el envio del packet mientras se esta reconectando
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
	public boolean isIgnorable() {
		return ignorable;
	}
	public void setIgnorable(boolean ignorable) {
		this.ignorable = ignorable;
	}
	public abstract Packet serialize(ByteArrayInputStream info) throws ReadPacketException;
	public abstract ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException;
}
