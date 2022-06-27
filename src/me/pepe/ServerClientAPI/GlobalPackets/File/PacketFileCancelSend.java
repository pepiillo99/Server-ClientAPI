package me.pepe.ServerClientAPI.GlobalPackets.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketFileCancelSend extends Packet {
	private String code;
	public PacketFileCancelSend() {
		super(0);
	}
	public PacketFileCancelSend(String code) {
		super(0);
		this.code = code;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String code = PacketUtilities.getString(info);
		return new PacketFileCancelSend(code);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(code, toInfo);
		return toInfo;
	}
	public String getCode() {
		return code;
	}
}
