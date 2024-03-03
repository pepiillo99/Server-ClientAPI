package me.pepe.ChatExample.Shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketDefineName extends Packet {
	private String name;
	public PacketDefineName() {
		super(1);
	}
	public PacketDefineName(String name) {
		super(1);
		this.name = name;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String name = PacketUtilities.getString(info);
		return new PacketDefineName(name);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(name, toInfo);
		return toInfo;
	}
	public String getName() {
		return name;
	}
}