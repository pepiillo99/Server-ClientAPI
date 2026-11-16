package me.pepe.ServerClientAPI.Connections;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetAddress;
import javax.net.ssl.SSLContext;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalNewConnection;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnect;

public abstract class ServerConnection implements AutoCloseable {
	private int port;
    private SSLContext tlsContext;
    private final AtomicInteger tlsPending = new AtomicInteger();
    private final ConcurrentHashMap<InetAddress, AtomicInteger> tlsByIP = new ConcurrentHashMap<InetAddress, AtomicInteger>();
	private AsynchronousServerSocketChannel serverSocket;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ServerClientAPI serverAPI;
    private final InetSocketAddress bindAddress;
	public ServerConnection(ServerClientAPI serverAPI, int port) { this(serverAPI,port,true); }
    protected ServerConnection(ServerClientAPI serverAPI,int port,boolean autoStart) {
        this(serverAPI,new InetSocketAddress(port),autoStart);
    }
    protected ServerConnection(ServerClientAPI serverAPI,InetSocketAddress address,boolean autoStart) {
        this.serverAPI=serverAPI; this.port=address.getPort(); this.bindAddress=address;
        if(autoStart) start();
    }
    public final void start() {
        if(!started.compareAndSet(false,true)) return;
        try {
			serverSocket = AsynchronousServerSocketChannel.open().bind(bindAddress);
            port=((InetSocketAddress)serverSocket.getLocalAddress()).getPort();
			onStart();
			serverSocket.accept(serverSocket, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {
				@Override
				public void completed(AsynchronousSocketChannel clientConnection, AsynchronousServerSocketChannel serverSock) {
					if (!serverSock.isOpen()) { closeChannel(clientConnection); return; }
                    serverSock.accept(serverSock, this);
                    if (serverSock.isOpen()) acceptConnection(clientConnection);
                    else closeChannel(clientConnection);
				}
				@Override
				public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
					if (!(exc instanceof ClosedChannelException)) {
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
			if (serverSocket != null && serverSocket.isOpen()) {
				serverSocket.close();
				onStop();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    @Override public final void close() { stop(); }

    public final void enableTls(SSLContext context) {
        if (started.get() || context == null) throw new IllegalStateException("TLS debe configurarse antes de iniciar el listener.");
        tlsContext = context;
    }
    private void acceptConnection(final AsynchronousSocketChannel channel) {
        if (tlsContext == null) { acceptTransport(channel); return; }
        final InetAddress address;
        try { address = ((InetSocketAddress) channel.getRemoteAddress()).getAddress(); }
        catch (IOException failure) { closeChannel(channel); return; }
        synchronized (tlsByIP) {
            AtomicInteger count = tlsByIP.get(address);
            if (tlsPending.get() >= 64 || (count != null && count.get() >= 8)) { closeChannel(channel); return; }
            if (count == null) { count = new AtomicInteger(); tlsByIP.put(address, count); }
            count.incrementAndGet(); tlsPending.incrementAndGet();
        }
        try {
            TlsSocketChannel.accept(channel, tlsContext, new CompletionHandler<TlsSocketChannel, Void>() {
                @Override public void completed(TlsSocketChannel secure, Void ignored) { release(); if (isConnected()) acceptTransport(secure); else closeChannel(secure); }
                @Override public void failed(Throwable failure, Void ignored) { release(); closeChannel(channel); }
                private void release() { releaseTlsAddress(address); }
            });
        } catch (IOException | RuntimeException failure) {
            releaseTlsAddress(address);
            closeChannel(channel);
        }
    }
    private void releaseTlsAddress(InetAddress address) {
        synchronized (tlsByIP) {
            tlsPending.decrementAndGet();
            AtomicInteger count = tlsByIP.get(address);
            if (count != null && count.decrementAndGet() == 0) tlsByIP.remove(address);
        }
    }
    private void closeChannel(AsynchronousSocketChannel channel) {
        try { channel.close(); } catch (IOException ignored) {}
    }
    private void acceptTransport(final AsynchronousSocketChannel clientConnection) {
        new ClientConnection(clientConnection, serverAPI, false, true) { // sin crear reconnectkey
            @Override
            public void onConnect() {}
            @Override protected boolean canReceivePacket(Packet packet) {
                return packet instanceof PacketGlobalNewConnection || packet instanceof PacketGlobalReconnect;
            }
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
                        close();
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
}