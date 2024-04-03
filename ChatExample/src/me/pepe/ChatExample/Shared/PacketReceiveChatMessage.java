package me.pepe.ChatExample.Shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketReceiveChatMessage extends Packet {
	private String color;
	private String user;
	private String message;
	public PacketReceiveChatMessage() {
		super(1);
	}
	public PacketReceiveChatMessage(String color, String user, String message) {
		super(1);
		this.color = color;
		this.user = user;
		this.message = message;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String color = PacketUtilities.getString(info);
		String user = PacketUtilities.getString(info);
		String message = PacketUtilities.getString(info);
		return new PacketReceiveChatMessage(color, user, message);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(color, toInfo);
		PacketUtilities.writeString(user, toInfo);
		PacketUtilities.writeString(message, toInfo);
		return toInfo;
	}
	public String getColor() {
		return color;
	}
	public String getUser() {
		return user;
	}
	public String getMessage() {
		return message;
	}
}