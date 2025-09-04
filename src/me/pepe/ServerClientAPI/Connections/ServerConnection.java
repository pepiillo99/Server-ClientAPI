package me.pepe.ServerClientAPI.Connections;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalDisconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalNewConnection;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnect;

public abstract class ServerConnection {
	private int port;
	private AsynchronousServerSocketChannel serverSocket;
	public ServerConnection(ServerClientAPI serverAPI, int port) {
		this.port = port;
		try {
			serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
			onStart();
			serverSocket.accept(serverSocket, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {
				@Override
				public void completed(AsynchronousSocketChannel clientConnection, AsynchronousServerSocketChannel serverSock) {
					serverSock.accept(serverSocket, this);
					new ClientConnection(clientConnection, serverAPI, false, true) { // sin crear reconnectkey
						@Override
						public void onConnect() {}
						@Override
						public void onFailedConnect() {}
						@Override
						public void onRecibe(Packet packet) {
							//System.out.println("packet recibido como pendiente " + packet.getClass().getName());
							if (packet instanceof PacketGlobalNewConnection) {
								ServerConnection.this.onConnect(clientConnection);
								killClient();
							} else if (packet instanceof PacketGlobalReconnect) {
								if (!ServerConnection.this.onReconnect(clientConnection, ((PacketGlobalReconnect) packet).getKey())) {
									sendPacket(new PacketGlobalDisconnect());
								} else {
									killClient();
								}
							} else {
								disconnect();
							}
						}
						@Override
						public void onDropCanReconnect() {}
						@Override
						public void onReconnect() {}
						@Override
						public void onDisconnect() {
							System.out.println("pendenting client disconnected");
						}						
					};
				}
				@Override
				public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
					if (exc.getClass() != AsynchronousCloseException.class) {
						System.err.println("Error al conectar un nuevo cliente:");
						exc.printStackTrace();
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public abstract void onStart();
	public abstract void onConnect(AsynchronousSocketChannel clientConnection);
	public abstract boolean onReconnect(AsynchronousSocketChannel clientConnection, String key);
	public abstract void onStop();
	public int getPort() {
		return port;
	}
	public boolean isConnected() {
		return serverSocket != null && serverSocket.isOpen();
	}
	public void stop() {
		try {
			if (serverSocket.isOpen()) {
				serverSocket.close();
				onStop();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}