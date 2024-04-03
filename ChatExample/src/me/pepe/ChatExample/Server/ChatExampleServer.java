package me.pepe.ChatExample.Server;

import me.pepe.ChatExample.Shared.PacketDefineName;
import me.pepe.ChatExample.Shared.PacketDefineNameRequest;
import me.pepe.ChatExample.Shared.PacketReceiveChatMessage;
import me.pepe.ChatExample.Shared.PacketSendChatMessage;
import me.pepe.ConsoleManager.Command;
import me.pepe.ConsoleManager.Console;
import me.pepe.ServerClientAPI.ServerClientAPI;

public class ChatExampleServer {
	private static ChatExampleServer instance;
	private ServerClientAPI scAPI;
	private Server server;
	public ChatExampleServer() {
		instance = this;
		Console console = new Console();
		regiterCommands(console);
		scAPI = new ServerClientAPI();
		registerPackets();
		server = new Server(scAPI);
	}
	private void registerPackets() {
		scAPI.addPacket(new PacketReceiveChatMessage());
		scAPI.addPacket(new PacketSendChatMessage());
		scAPI.addPacket(new PacketDefineName());
		scAPI.addPacket(new PacketDefineNameRequest());
	}
	public static ChatExampleServer getInstance() {
		return instance;
	}
	public Server getServer() {
		return server;
	}
	public void regiterCommands(Console console) {
		new Command("list", console, "Muestra la lista de usuarios conectados") {
			@Override
			public void execute(String[] args) {
				if (server.getClients().isEmpty()) {
					System.out.println("No hay usuarios conectados al chat...");
				} else {
					System.out.println("Usuarios conectados al chat:");
					for (ServerClient sc : server.getClients()) {
						System.out.println("- " + sc.getName() + " ID: " + sc.getID());
					}
				}
			}			
		};
		new Command("drop", console, "Cancela la conexión de un cliente (se puede volver a conectar)") {
			@Override
			public void execute(String[] args) {
				if (args.length == 1) {
					try {
						int userID = Integer.valueOf(args[0]);
						ServerClient sc = server.getClient(userID);
						if (sc != null) {
							sc.dropAndReconnect();
						} else {
							System.out.println("El usuario con la ID " + userID + " no está conectado, usa 'list' para comprobar los usuarios conectados");
						}
					} catch (NumberFormatException ex) {
						System.out.println(args[0] + " no es un numero válido");
					}
				} else {
					System.out.println("Uso incorrecto, usa 'drop <userID>'");
				}
			}			
		};
		new Command("disconnect", console, "Desconecta al usuario (no vuelve a conectarse)") {
			@Override
			public void execute(String[] args) {
				if (args.length == 1) {
					try {
						int userID = Integer.valueOf(args[0]);
						ServerClient sc = server.getClient(userID);
						if (sc != null) {
							sc.disconnect();
						} else {
							System.out.println("El usuario con la ID " + userID + " no está conectado, usa 'list' para comprobar los usuarios conectados");
						}
					} catch (NumberFormatException ex) {
						System.out.println(args[0] + " no es un numero válido");
					}
				} else {
					System.out.println("Uso incorrecto, usa 'drop <userID>'");
				}
			}			
		};
	}
	public static void main(String[] args) {
		new ChatExampleServer();
	}
}
