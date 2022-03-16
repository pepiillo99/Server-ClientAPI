package me.pepe.ServerClientAPI.Connections;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public abstract class ServerConnection {
	private int port;
	private AsynchronousServerSocketChannel serverSocket;
	public ServerConnection(int port) {
		this.port = port;
		try {
			serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
			onStart();
			serverSocket.accept(serverSocket, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {
				@Override
				public void completed(AsynchronousSocketChannel clientConnection, AsynchronousServerSocketChannel serverSock) {
					serverSock.accept(serverSocket, this);
					onConnect(clientConnection);
				}
				@Override
				public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
					if (exc.getClass() != AsynchronousCloseException.class) {
						System.out.println("Error al conectar un nuevo cliente:");
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