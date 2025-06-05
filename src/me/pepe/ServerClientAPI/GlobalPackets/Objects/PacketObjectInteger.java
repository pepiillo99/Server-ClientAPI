package me.pepe.ServerClientAPI.GlobalPackets.Objects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketObjectInteger extends Packet {
	private int object;
	public PacketObjectInteger() {
		super(1);
	}
	public PacketObjectInteger(int object) {
		super(1);
		this.object = object;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		int object = PacketUtilities.getInteger(info);
		return new PacketObjectInteger(object);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeInteger(object, toInfo);
		return toInfo;
	}
	public int getObject() {
		return object;
	}
}
