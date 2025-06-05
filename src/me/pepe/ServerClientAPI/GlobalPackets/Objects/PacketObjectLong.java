package me.pepe.ServerClientAPI.GlobalPackets.Objects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketObjectLong extends Packet {
	private long object;
	public PacketObjectLong() {
		super(1);
	}
	public PacketObjectLong(long object) {
		super(1);
		this.object = object;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		long object = PacketUtilities.getLong(info);
		return new PacketObjectLong(object);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeLong(object, toInfo);
		return toInfo;
	}
	public long getObject() {
		return object;
	}
}