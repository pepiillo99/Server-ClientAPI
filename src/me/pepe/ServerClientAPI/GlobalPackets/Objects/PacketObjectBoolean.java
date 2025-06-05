package me.pepe.ServerClientAPI.GlobalPackets.Objects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketObjectBoolean extends Packet {
	private boolean object;
	public PacketObjectBoolean() {
		super(1);
	}
	public PacketObjectBoolean(boolean object) {
		super(1);
		this.object = object;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		boolean object = PacketUtilities.getBoolean(info);
		return new PacketObjectBoolean(object);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeBoolean(object, toInfo);
		return toInfo;
	}
	public boolean getObject() {
		return object;
	}
}
