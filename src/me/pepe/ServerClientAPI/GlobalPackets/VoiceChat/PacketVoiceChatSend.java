package me.pepe.ServerClientAPI.GlobalPackets.VoiceChat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;

public class PacketVoiceChatSend extends Packet {
	private byte[] data;
	public PacketVoiceChatSend(byte[] data) {
		super(0);
		this.data = data;
	}
	public PacketVoiceChatSend() {
		super(0);
	}
	@Override
	public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
		System.out.println("Enviado " + info.available());
		byte[] data = PacketUtilities.getBytes(info);
		return new PacketVoiceChatSend(data);
	}
	@Override
	public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
		PacketUtilities.writeBytes(data, toInfo);
		return toInfo;
	}
	public byte[] getData() {
		return data;
	}
	public PacketVoiceChatReceive toSend(int channelID) {
		return new PacketVoiceChatReceive(channelID, data);
	}
}