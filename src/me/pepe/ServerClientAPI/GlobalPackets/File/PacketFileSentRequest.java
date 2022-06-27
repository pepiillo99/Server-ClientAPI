package me.pepe.ServerClientAPI.GlobalPackets.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketFileSentRequest extends Packet {
	private String code;
	private boolean request;
	public PacketFileSentRequest() {
		super(0);
	}
	public PacketFileSentRequest(String code, boolean request) {
		super(0);
		this.code = code;
		this.request = request;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String code = PacketUtilities.getString(info);
		boolean request = PacketUtilities.getBoolean(info);
		return new PacketFileSentRequest(code, request);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(code, toInfo);
		PacketUtilities.writeBoolean(request, toInfo);
		return toInfo;
	}
	public String getCode() {
		return code;
	}
	public boolean canSent() {
		return request;
	}
}
