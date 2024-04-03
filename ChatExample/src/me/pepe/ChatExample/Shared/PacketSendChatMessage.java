package me.pepe.ChatExample.Shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketSendChatMessage extends Packet {
	private String message;
	public PacketSendChatMessage() {
		super(1);
	}
	public PacketSendChatMessage(String message) {
		super(1);
		this.message = message;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String message = PacketUtilities.getString(info);
		return new PacketSendChatMessage(message);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(message, toInfo);
		return toInfo;
	}
	public String getMessage() {
		return message;
	}
}