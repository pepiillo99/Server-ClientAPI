package me.pepe.ServerClientAPI.GlobalPackets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;

public class PacketGlobalAskNewConnection extends Packet {
	public PacketGlobalAskNewConnection() {
		super(0);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		return new PacketGlobalAskNewConnection();
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		return toInfo;
	}
}
