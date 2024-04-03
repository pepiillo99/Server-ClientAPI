package me.pepe.ChatExample.Server;

import java.nio.channels.AsynchronousSocketChannel;

import me.pepe.ChatExample.Shared.PacketDefineName;
import me.pepe.ChatExample.Shared.PacketDefineNameRequest;
import me.pepe.ChatExample.Shared.PacketReceiveChatMessage;
import me.pepe.ChatExample.Shared.PacketSendChatMessage;
import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public class ServerClient extends ClientConnection {
	private int id;
	private String name;
	private String color;
	public ServerClient(int id, AsynchronousSocketChannel connection, ServerClientAPI packetManager) {
		super(connection, packetManager);
		this.id = id;
	}
	@Override
	public void onConnect() {
		System.out.println("&aNew client connected");
	}
	@Override
	public void onFailedConnect() {}
	@Override
	public void onRecibe(Packet packet) {
		if (packet instanceof PacketDefineName) {
			PacketDefineName pdn = (PacketDefineName) packet;
			boolean request = false;
			if (name == null || name.isEmpty()) {
				if (!ChatExampleServer.getInstance().getServer().hasUser(pdn.getName())) {
					ChatExampleServer.getInstance().getServer().broadcastMessage("a", pdn.getName() + " se unió al chat");
					name = pdn.getName();
					request = true;
					color = Utils.getRandomColor();
					sendPacket(new PacketReceiveChatMessage(color, "Server", "Se te ha establecido este color aleatoriamente en el chat"));
				}
			}
			sendPacket(new PacketDefineNameRequest(request, pdn.getName()));
		} else if (packet instanceof PacketSendChatMessage) {
			PacketSendChatMessage scm = (PacketSendChatMessage) packet;
			for (ServerClient sc : ChatExampleServer.getInstance().getServer().getClients()) {
				if (sc.hasName()) {
					if (sc != this) {
						sc.sendPacket(new PacketReceiveChatMessage(color, name, scm.getMessage()));
					} else {
						sc.sendPacket(new PacketReceiveChatMessage(color, "Tú", scm.getMessage()));
					}
				}
			}
		}
	}
	@Override
	public void onDropCanReconnect() {
		System.out.println("&cConexión perdida con " + name);
	}
	@Override
	public void onReconnect() {
		System.out.println("&aCliente " + name + " reconectado");
	}
	@Override
	public void onDisconnect() {
		System.out.println("&cCliente " + name + " desconectado");
		if (name != null && !name.isEmpty()) {
			ChatExampleServer.getInstance().getServer().broadcastMessage("c", name + " salió del chat");
		}
	}
	public int getID() {
		return id;
	}
	public String getName() {
		return name;
	}
	public boolean hasName() {
		return name != null && !name.isEmpty();
	}
}