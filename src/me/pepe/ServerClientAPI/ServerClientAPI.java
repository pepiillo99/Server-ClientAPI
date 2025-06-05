package me.pepe.ServerClientAPI;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalAskNewConnection;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalDisconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalKick;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalNewConnection;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalPing;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnectDefineKey;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCanChangeBytesPerPacket;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCanSent;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCancelSend;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFilePartOfFile;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFilePartOfFileReceived;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileSentRequest;
import me.pepe.ServerClientAPI.GlobalPackets.Objects.PacketObjectBoolean;
import me.pepe.ServerClientAPI.GlobalPackets.Objects.PacketObjectInteger;
import me.pepe.ServerClientAPI.GlobalPackets.Objects.PacketObjectLong;
import me.pepe.ServerClientAPI.GlobalPackets.Objects.PacketObjectString;
import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChannelClosed;
import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChatReceive;
import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChatSend;

public class ServerClientAPI {
	private int packetSize = 1;
	private HashMap<Integer, Packet> packets = new HashMap<Integer, Packet>();
	private HashMap<Class<? extends Packet>, Integer> packetClassID = new HashMap<Class<? extends Packet>, Integer>();
	public ServerClientAPI() {
		registerPackets();
	}
	public int getPacketID(Class<? extends Packet> packetClass) {
		if (packetClassID.containsKey(packetClass)) {
			return packetClassID.get(packetClass);
		} else {
			return 0;
		}
	}
	public Packet getPacket(int packetID, ByteArrayInputStream info) throws ReadPacketException {
		if (packets.containsKey(packetID)) {
			return packets.get(packetID).serialize(info);
		} else {
			return null;
		}
	}
	public void addPacket(Packet packet) {
		int packetID = packetSize++;
		packets.put(packetID, packet);
		packetClassID.put(packet.getClass(), packetID);
	}
	private void registerPackets() {
		// GLOBAL PACKETS (0)
		addPacket(new PacketGlobalNewConnection());
		addPacket(new PacketGlobalAskNewConnection());
		addPacket(new PacketGlobalReconnect());
		addPacket(new PacketGlobalReconnectDefineKey());
		addPacket(new PacketGlobalPing());
		addPacket(new PacketGlobalKick());
		addPacket(new PacketGlobalDisconnect());
		addPacket(new PacketFileCanSent());
		addPacket(new PacketFilePartOfFile());
		addPacket(new PacketFilePartOfFileReceived());
		addPacket(new PacketFileSentRequest());
		addPacket(new PacketFileCancelSend());
		addPacket(new PacketFileCanChangeBytesPerPacket());
		addPacket(new PacketVoiceChannelClosed());
		addPacket(new PacketVoiceChatReceive());
		addPacket(new PacketVoiceChatSend());
		
		// OBJECT PACKETS
		addPacket(new PacketObjectBoolean());
		addPacket(new PacketObjectString());
		addPacket(new PacketObjectInteger());
		addPacket(new PacketObjectLong());
	}
}