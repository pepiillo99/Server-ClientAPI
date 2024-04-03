package me.pepe.ChatExample.Client;

import java.io.IOException;

import me.pepe.ChatExample.Shared.PacketDefineName;
import me.pepe.ChatExample.Shared.PacketDefineNameRequest;
import me.pepe.ChatExample.Shared.PacketReceiveChatMessage;
import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public class Client extends ClientConnection {
	private String name;
	public Client(String ip, int port, ServerClientAPI packetManager) throws IOException {
		super(ip, port, packetManager);
	}
	@Override
	public void onConnect() {
		System.out.println("&aConexión establecida correctamente, introduzca el nombre que desea tener en el chat");
	}
	@Override
	public void onFailedConnect() {
		ChatExampleClient.getInstance().failedConnect();
	}
	@Override
	public void onRecibe(Packet packet) {
		if (packet instanceof PacketDefineNameRequest) {
			PacketDefineNameRequest request = (PacketDefineNameRequest) packet;
			if (request.getRequest()) {
				name = request.getName();
				System.out.println("&aNombre aceptado por el servidor, escribiras por el chat con el nombre de '" + name + "'");
			} else {
				System.out.println("&cNombre solicitado '" + request.getName() + "' no aceptado por el servidor, posiblemente otro usuario ya se llame asi, prueba otro nombre");
			}
		} else if (packet instanceof PacketReceiveChatMessage) {
			PacketReceiveChatMessage receive = (PacketReceiveChatMessage) packet;
			System.out.println("&" + receive.getColor() + "" + receive.getUser() + ": " + receive.getMessage());
		}
	}
	@Override
	public void onDropCanReconnect() {
		System.out.println("&cConexión perdida, intentando reconectar...");
	}
	@Override
	public void onReconnect() {
		System.out.println("&a¡Reconectado exitosamente!");
	}
	@Override
	public void onDisconnect() {
		System.out.println("Desconectado del servidor!");
		System.exit(0);
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		sendPacket(new PacketDefineName(name)); // se solicita al servidor y el servidor lo acepta o rechaza
	}
}