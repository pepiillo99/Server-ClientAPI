package me.pepe.ChatExample.Server;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.Collection;
import java.util.HashMap;

import me.pepe.ChatExample.Shared.PacketReceiveChatMessage;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Connections.ServerConnection;

public class Server extends ServerConnection {
	private ServerClientAPI scAPI;
	private int clientCounter = 0;
	private HashMap<Integer, ServerClient> clients = new HashMap<Integer, ServerClient>();
	public Server(ServerClientAPI serverAPI) {
		super(serverAPI, 777);
		this.scAPI = serverAPI;
	}
	@Override
	public void onStart() {
		System.out.println("&2Chat service online!");
	}
	@Override
	public void onConnect(AsynchronousSocketChannel clientConnection) {
		clientCounter++;
		clients.put(clientCounter, new ServerClient(clientCounter, clientConnection, scAPI));
	}
	@Override
	public boolean onReconnect(AsynchronousSocketChannel clientConnection, String key) {
		ServerClient sc = getByReconnectKey(key);
		if (sc != null) {
			sc.reconnect(clientConnection);
		}
		return sc != null;
	}
	@Override
	public void onStop() {
		System.out.println("&4Chat service offline!");
	}
	public Collection<ServerClient> getClients() {
		return clients.values();
	}
	public ServerClient getClient(int id) {
		if (clients.containsKey(id)) {
			return clients.get(id);
		}
		return null;
	}
	public boolean hasUser(String user) {
		for (ServerClient sc : getClients()) {
			if (sc.hasName() && sc.getName().toLowerCase().equals(user.toLowerCase())) {
				return true;
			}
		}
		return false;
	}
	public ServerClient getByReconnectKey(String key) {
		for (ServerClient sc : getClients()) {
			if (sc.checkReconnectKey(key)) {
				return sc;
			}
		}
		return null;
	}
	public void broadcastMessage(String color, String message) {
		System.out.println("&" + color + "Server: " + message);
		for (ServerClient sc : getClients()) {
			if (sc.hasName()) {
				sc.sendPacket(new PacketReceiveChatMessage(color, "Server", message));
			}
		}
	}
}