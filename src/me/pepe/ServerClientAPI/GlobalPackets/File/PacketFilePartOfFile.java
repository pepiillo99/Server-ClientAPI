package me.pepe.ServerClientAPI.GlobalPackets.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketFilePartOfFile extends Packet {
	private String code;
	private byte[] bytes;
	public PacketFilePartOfFile() {
		super(0);
	}
	public PacketFilePartOfFile(String code, byte[] bytes) {
		super(0);
		this.code = code;
		this.bytes = bytes;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String code = PacketUtilities.getString(info);
		byte[] bytes = PacketUtilities.getBytes(info);
		return new PacketFilePartOfFile(code, bytes);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(code, toInfo);
		PacketUtilities.writeBytes(bytes, toInfo);
		return toInfo;
	}
	public String getCode() {
		return code;
	}
	public byte[] getBytes() {
		return bytes;
	}
}
