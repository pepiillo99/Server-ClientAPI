package me.pepe.ServerClientAPI.GlobalPackets.VoiceChat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketVoiceChannelClosed extends Packet {
	private int id;
	public PacketVoiceChannelClosed(int id) {
		super(0);
		this.id = id;
	}
	public PacketVoiceChannelClosed() {
		super(0);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		int id = PacketUtilities.getInteger(info);
		return new PacketVoiceChannelClosed(id);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		return toInfo;
	}
	public int getChannelID() {
		return id;
	}
}
