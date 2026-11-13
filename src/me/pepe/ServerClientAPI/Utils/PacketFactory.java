package me.pepe.ServerClientAPI.Utils;
import me.pepe.ServerClientAPI.Packet;
public interface PacketFactory { Packet create(int attempt) throws Exception; }
