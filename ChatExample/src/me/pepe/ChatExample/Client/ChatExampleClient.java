package me.pepe.ChatExample.Client;

import java.io.IOException;

import me.pepe.ChatExample.Shared.PacketDefineName;
import me.pepe.ChatExample.Shared.PacketDefineNameRequest;
import me.pepe.ChatExample.Shared.PacketReceiveChatMessage;
import me.pepe.ChatExample.Shared.PacketSendChatMessage;
import me.pepe.ConsoleManager.CommandError;
import me.pepe.ConsoleManager.Console;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Utils.CalculatorNetworkMaskOpenned;
import me.pepe.ServerClientAPI.Utils.NetworkMaskDoneCallback;
import me.pepe.ServerClientAPI.Utils.NetworkMaskFindedCallback;

public class ChatExampleClient {
	private static ChatExampleClient instance;
	private ServerClientAPI scAPI;
	private Console console;
	private Client client;
	private String conexionIP;
	public ChatExampleClient() {
		instance = this;
		scAPI = new ServerClientAPI();
		registerPackets();
		console = new Console();
		console.setCommandErrorAction(new CommandError() {
			@Override
			public void execute(String cmd) {
				if (conexionIP == null || conexionIP.isEmpty()) {
					conexionIP = cmd;
					System.out.println("&aIntentando conectar a la IP introducida &e" + conexionIP);
					tryConnect();
				} else if (client != null && client.isConnected()) {
					if (client.getName() == null || client.getName().isEmpty()) {
						if (cmd.contains(" ")) {
							System.out.println("&cEl nombre no puede contener espacios, escribe denuevo un nombre válido");
						} else if (cmd.length() < 3 || cmd.length() > 10) {
							System.out.println("&cEl nombre no puede tener menos de 3 caracteres ni mas de 10, escribe denuevo un nombre válido");
						} else {
							client.setName(cmd);
						}
					} else {
						client.sendPacket(new PacketSendChatMessage(cmd));
					}
				} else {
					System.out.println("&c¡Espera que se realice la conexión!");
				}
			}
		});
		System.out.println("&ePor favor introduce la IP del servidor que desea conectarse...");
	}
	public static ChatExampleClient getInstance() {
		return instance;
	}
	private void tryConnect() {
		try {
			client = new Client(conexionIP, 777, scAPI);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void failedConnect() {
		System.out.println("&cError al conectar con " + conexionIP + ":" + 777 + " por favor revise la IP denuevo...");
		conexionIP = null;
		client = null;
	}
	private void registerPackets() {
		scAPI.addPacket(new PacketReceiveChatMessage());
		scAPI.addPacket(new PacketSendChatMessage());
		scAPI.addPacket(new PacketDefineName());
		scAPI.addPacket(new PacketDefineNameRequest());
	}
	public static void main(String[] args) {
		new ChatExampleClient();
	}
}
 