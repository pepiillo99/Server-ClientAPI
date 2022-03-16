package me.pepe.ServerClientAPI.GlobalPackets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;

public class PacketGlobalPing extends Packet {
	public PacketGlobalPing() {
		super(0);
		setIgnorable(true);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		return new PacketGlobalPing();
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		return toInfo;
	}
}