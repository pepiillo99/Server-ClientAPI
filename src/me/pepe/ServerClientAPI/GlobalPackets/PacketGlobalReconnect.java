package me.pepe.ServerClientAPI.GlobalPackets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketGlobalReconnect extends Packet {
	private String key;
	public PacketGlobalReconnect(String key) {
		super(0);
		this.key = key;
	}
	public PacketGlobalReconnect() {
		super(0);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String key = PacketUtilities.getString(info);
		return new PacketGlobalReconnect(key);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(key, toInfo);
		return toInfo;
	}
	public String getKey() {
		return key;
	}
}