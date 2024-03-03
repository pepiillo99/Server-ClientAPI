package me.pepe.ChatExample.Shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketDefineNameRequest extends Packet {
	private boolean request;
	private String name;
	public PacketDefineNameRequest() {
		super(1);
	}
	public PacketDefineNameRequest(boolean request, String name) {
		super(1);
		this.request = request;
		this.name = name;
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		boolean request = PacketUtilities.getBoolean(info);
		String name = PacketUtilities.getString(info);
		return new PacketDefineNameRequest(request, name);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeBoolean(request, toInfo);
		PacketUtilities.writeString(name, toInfo);
		return toInfo;
	}
	public boolean getRequest() {
		return request;
	}
	public String getName() {
		return name;
	}
}