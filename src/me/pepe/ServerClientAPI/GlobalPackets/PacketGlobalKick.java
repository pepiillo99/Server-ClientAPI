package me.pepe.ServerClientAPI.GlobalPackets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketGlobalKick extends Packet {
	private String motive;
	public PacketGlobalKick(String motive) {
		super(0);
		this.motive = motive;
	}
	public PacketGlobalKick() {
		super(0);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		String motive = PacketUtilities.getString(info);
		return new PacketGlobalKick(motive);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeString(motive, toInfo);
		return toInfo;
	}
	public String getMotive() {
		return motive;
	}
}