package me.pepe.ServerClientAPI.GlobalPackets.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketFileCanSent extends Packet {
	private String code;
	private String fileType;
	private String path;
	private long bytesPerPacket;
	private long fileLenght;
	public PacketFileCanSent() {
		super(0);
	}
	public PacketFileCanSent(String code, String fileType, String path, long bytesPerPacket, long fileLenght) {
		super(0);
		this.code = code;
		this.fileType = fileType;
		this.path = path;
		this.bytesPerPacket = bytesPerPacket;
		this.fileLenght = fileLenght;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String code = PacketUtilities.getString(info);
		String fileType = PacketUtilities.getString(info);
		String path = PacketUtilities.getString(info);
		long bytesPerPacket = PacketUtilities.getLong(info);
		long fileLenght = PacketUtilities.getLong(info);
		return new PacketFileCanSent(code, fileType, path, bytesPerPacket, fileLenght);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(code, toInfo);
		PacketUtilities.writeString(fileType, toInfo);
		PacketUtilities.writeString(path, toInfo);
		PacketUtilities.writeLong(bytesPerPacket, toInfo);
		PacketUtilities.writeLong(fileLenght, toInfo);
		return toInfo;
	}
	public String getCode() {
		return code;
	}
	public String getFileType() {
		return fileType;
	}
	public String getPath() {
		return path;
	}
	public long getBytesPerPacket() {
		return bytesPerPacket;
	}
	public long getFileLenght() {
		return fileLenght;
	}
}
