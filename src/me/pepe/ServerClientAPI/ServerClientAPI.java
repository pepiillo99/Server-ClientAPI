package me.pepe.ServerClientAPI;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalDisconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalKick;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalPing;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnectDefineKey;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCanChangeBytesPerPacket;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCanSent;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCancelSend;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFilePartOfFile;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFilePartOfFileReceived;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileSentRequest;

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
	}
}