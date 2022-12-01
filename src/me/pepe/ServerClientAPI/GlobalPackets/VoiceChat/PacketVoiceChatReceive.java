package me.pepe.ServerClientAPI.GlobalPackets.VoiceChat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketVoiceChatReceive extends Packet {
	private int channelID;
	private byte[] data;
	public PacketVoiceChatReceive(int channelID, byte[] data) {
		super(0);
		this.channelID = channelID;
		this.data = data;
	}
	public PacketVoiceChatReceive() {
		super(0);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		int channelID = PacketUtilities.getInteger(info);
		byte[] data = PacketUtilities.getBytes(info);		
		return new PacketVoiceChatReceive(channelID, data);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeInteger(channelID, toInfo);
		PacketUtilities.writeBytes(data, toInfo);
		return toInfo;
	}
	public int getChannelID() {
		return channelID;
	}
	public byte[] getData() {
		return data;
	}
}