package me.pepe.ServerClientAPI.GlobalPackets.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketFilePartOfFileReceived extends Packet {
	private String code;
	private long bytesLenght;
	public PacketFilePartOfFileReceived() {
		super(0);
	}
	public PacketFilePartOfFileReceived(String code, long bytesLenght) {
		super(0);
		this.code = code;
		this.bytesLenght = bytesLenght;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String code = PacketUtilities.getString(info);
		long bytesLenght = PacketUtilities.getLong(info);
		return new PacketFilePartOfFileReceived(code, bytesLenght);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(code, toInfo);
		PacketUtilities.writeLong(bytesLenght, toInfo);
		return toInfo;
	}
	public String getCode() {
		return code;
	}
	public long getBytesLenght() {
		return bytesLenght;
	}
}
