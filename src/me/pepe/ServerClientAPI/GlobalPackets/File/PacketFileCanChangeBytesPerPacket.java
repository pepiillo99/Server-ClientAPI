package me.pepe.ServerClientAPI.GlobalPackets.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketFileCanChangeBytesPerPacket extends Packet {
	private String code;
	private long bytes;
	public PacketFileCanChangeBytesPerPacket() {
		super(0);
	}
	public PacketFileCanChangeBytesPerPacket(String code, long bytes) {
		super(0);
		this.code = code;
		this.bytes = bytes;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String code = PacketUtilities.getString(info);
		long bytes = PacketUtilities.getLong(info);
		return new PacketFileCanChangeBytesPerPacket(code,  bytes);
	}

	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(code, toInfo);
		PacketUtilities.writeLong(bytes, toInfo);
		return toInfo;
	}
	public String getCode() {
		return code;
	}
	public long getBytes() {
		return bytes;
	}
}