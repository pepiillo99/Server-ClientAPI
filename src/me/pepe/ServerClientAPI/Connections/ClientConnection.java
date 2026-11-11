package me.pepe.ServerClientAPI.Connections;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.WritePendingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.LineUnavailableException;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalAskNewConnection;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalDisconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalKick;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalNewConnection;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalPing;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnectDefineKey;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCanChangeBytesPerPacket;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCanSent;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileCancelSend;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFilePartOfFile;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFilePartOfFileReceived;
import me.pepe.ServerClientAPI.GlobalPackets.File.PacketFileSentRequest;
import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChatReceive;
import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChatSend;
import me.pepe.ServerClientAPI.Utils.AwaitAnswerCallback;
import me.pepe.ServerClientAPI.Utils.PacketSentCallback;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;
import me.pepe.ServerClientAPI.Utils.StoppableThread;
import me.pepe.ServerClientAPI.Utils.Utils;
import me.pepe.ServerClientAPI.Utils.File.FileReceiver;
import me.pepe.ServerClientAPI.Utils.File.FileSender;
import me.pepe.ServerClientAPI.VoiceChat.AudioChannel;
import me.pepe.ServerClientAPI.VoiceChat.MicrophoneThread;

public abstract class ClientConnection {
	private ServerClientAPI packetManager;
	private volatile AsynchronousSocketChannel connection;
	private String ip;
	private int port;
	private ClientConnectionType clientConnectionType = null;
	private ConcurrentSkipListMap<Long, Packet> pendentingSendPacket = new ConcurrentSkipListMap<Long, Packet>();
	private volatile long maxPacketSizeSend = Utils.getFromSacledBytes("5KB");
	private long defaultMaxPacketSizeSend = maxPacketSizeSend;
	private volatile long maxPacketSizeReceive = Utils.getFromSacledBytes("5KB");
	private long defaultMaxPacketSizeReceive = maxPacketSizeReceive;
	private int nextPacketSize;
	private long downPing = 0;
	private volatile boolean reading = false;
	private String ipC = "";
	public boolean debugMode = false;
	private volatile boolean connectionCompleted = false; // first connection is completed?
	private long lastTryPacketSent = 0;
	private long lastPacketSent = 0;
	private long lastBytePerSecondUpdate = 0; // la ultima vez que actualizó los bytes por segundo
	// b/s Received
	private volatile long bytesPerSecondReceived = 0;
	private long newbytesPerSecondReceived = 0;	
	// b/s sent
	private volatile long bytesPerSecondSent = 0;
	private long newbytesPerSecondSent = 0;
	private volatile long bytesSent = 0;
	private volatile long bytesReceived = 0;
	public boolean byteDebug = false;
	private volatile Packet sendingPacket = null;
	private volatile boolean reconnecting = false;
	private String reconnectKey = "";
	private volatile long timeOut = 5000;
	private volatile long lastPinged = 0;
	private StoppableThread timeOutThread;
	private int packetsent = 0;
	private int packetReceived = 0;
	private boolean canReconnect = true;
	private volatile boolean connected = false; // para saber si al desconectar puede reconectar o definitivamente se desconecto
	private StoppableThread reconnectThread; // thread que define si la conexion caduca en el servidor
	private long maxTimePerPacketOnSendFiled = 500;
	private boolean lastPacketSendFileChangedByteAmount = false;
	private final ConcurrentHashMap<String, FileSender> filesSending = new ConcurrentHashMap<String, FileSender>();
	private final ConcurrentHashMap<String, FileReceiver> filesReceiver = new ConcurrentHashMap<String, FileReceiver>();
	// voice chat
	private boolean canEarn = false;
	private boolean canSpeak = false;
	private MicrophoneThread micThread;
	private HashMap<Integer, AudioChannel> audioChannels = new HashMap<Integer, AudioChannel>();
	//
	private final AtomicInteger answerCounter = new AtomicInteger(1);
	private final Object sendLock = new Object();
	private volatile boolean disconnected;
	private static final SecureRandom secureRandom = new SecureRandom();
	private static final int MAX_FRAME_BYTES = FileSender.MAX_BYTES_PER_PACKET + 1024;
	private final ConcurrentHashMap<Integer, AwaitAnswerCallback> awaitAnswers = new ConcurrentHashMap<Integer, AwaitAnswerCallback>();
	public ClientConnection(AsynchronousSocketChannel connection, ServerClientAPI packetManager) {
		this(connection, packetManager, true, true);
	}
	public ClientConnection(AsynchronousSocketChannel connection, ServerClientAPI packetManager, boolean startRead) {
		this(connection, packetManager, true, startRead);
	}
	protected ClientConnection(AsynchronousSocketChannel connection, ServerClientAPI packetManager, boolean createReconnectKey, boolean startRead) {
		this.connection = connection;
		this.packetManager = packetManager;
		this.clientConnectionType = ClientConnectionType.SERVER_TO_CLIENT;
		try {
			SocketAddress socketAddress = connection.getRemoteAddress();
			if (socketAddress instanceof InetSocketAddress) {
				InetAddress inetAddress = ((InetSocketAddress)socketAddress).getAddress();
				if (inetAddress instanceof Inet4Address) {
					ipC = inetAddress.getHostAddress();
					//ipC = ipC.replace(":", "");
				} else if (inetAddress instanceof Inet6Address) {
					ipC = inetAddress.getHostAddress();
					//ipC = ipC.replace(":", "");
				} else {
					ipC = "Error(Not an IP address of client.)";
				}
			} else {
				ipC = "Error(Not an internet protocol socket.)";
			}
		} catch (IOException e) {
			ipC = "Error(Error al coger la ip)";
			e.printStackTrace();
		}
		lastPinged = System.currentTimeMillis();
		if (createReconnectKey) {
			reconnectKey = new BigInteger(256, secureRandom).toString(32);
			PacketGlobalReconnectDefineKey rdkPacket = new PacketGlobalReconnectDefineKey(reconnectKey);
			rdkPacket.setSentCallback(new PacketSentCallback() {
				@Override
				public void onSent(long miliseconds) {
					connected = true;
					onConnect();
					if (startRead) {
						startRead();
					}
				}			
			});
			connectionCompleted = true;
			sendPacket(rdkPacket);
			timeOutThread = new StoppableThread() {
				@Override
				public void run() {
					while (isRunning()) {
						try {
							sleep(1000);
							checkTimeOutAwaitAnswers();
						} catch (InterruptedException e) {}
						if (isConnected() && !reconnecting && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
							System.out.println("Client connection time out! ");
							dropAndReconnect();
						}
					}
				}
			};
		} else {
			timeOutThread = new StoppableThread() {
				@Override
				public void run() {
					try {
						sleep(1000);
						checkTimeOutAwaitAnswers();
						if (!hasReconnectKey()) {
							System.out.println("Pendenting connection dropped by timeout");
							totalDisconnect();
						}
					} catch (InterruptedException e) {}
					if (isConnected() && !reconnecting && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
						System.out.println("Client connection time out! ");
						dropAndReconnect();
					}
				}
			};
			PacketGlobalAskNewConnection rdkPacket = new PacketGlobalAskNewConnection();
			rdkPacket.setSentCallback(new PacketSentCallback() {
				@Override
				public void onSent(long miliseconds) {
					connected = true;
					onConnect();
					if (startRead) {
						startRead();
					}
				}			
			});
			connectionCompleted = true;
			sendPacket(rdkPacket);
		}
		if (!disconnected) {
			timeOutThread.start();
		}
	}
	public ClientConnection(String ip, int port, ServerClientAPI packetManager) throws IOException {
		this.packetManager = packetManager;
		this.ip = ip;
		this.port = port;
		connection = AsynchronousSocketChannel.open();
		this.clientConnectionType = ClientConnectionType.CLIENT_TO_SERVER;
		timeOutThread = new StoppableThread() {
			@Override
			public void run() {
				while (isRunning()) {
					try {
						sleep(1000);
						checkTimeOutAwaitAnswers();
					} catch (InterruptedException e) {}
					if (isConnected()) {
						try {
							sendPacket(new PacketGlobalPing());
						} catch (NotYetConnectedException ex) {
							onFailedConnect();
							dropAndReconnect();
						}
					}
					if (isConnected() && !reconnecting && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
						System.out.println("Server connection time out! ");
						dropAndReconnect();
					}
				}
			}
		};
		try {
			connection.connect(new InetSocketAddress(ip, port), connection, new CompletionHandler<Void, AsynchronousSocketChannel>() {
				@Override
				public void completed(Void result, AsynchronousSocketChannel attachment) {	
					connectionCompleted = true;
					lastPinged = System.currentTimeMillis();
					sendPacket(new PacketGlobalNewConnection());
					if (!disconnected) {
						timeOutThread.start();
					}
					startRead();
				}
				@Override
				public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
					System.out.println("Error al conectar con el servidor " + ip + ":" + port);
					onFailedConnect();
					totalDisconnect(false);
				}
			});
		} catch (UnresolvedAddressException ex) {
			onFailedConnect();
			totalDisconnect(false);
		}
	}
	public ClientConnectionType getClientConnectionType() {
		return clientConnectionType;
	}
	public abstract void onConnect();
	public abstract void onFailedConnect();
	public abstract void onRecibe(Packet packet);
	public abstract void onDropCanReconnect();
	public abstract void onReconnect();
	public abstract void onDisconnect();
	public void informClient() {
		System.out.println("Hay " + this.pendentingSendPacket.size() + " packets pendientes para enviar");
		System.out.println("Ultimo packet enviado hace " + (System.currentTimeMillis() - lastPacketSent) + " ms");
		System.out.println("Se intento enviar un packet hace " + "(" + lastTryPacketSent + " - " + System.currentTimeMillis() + ")" + ((lastTryPacketSent + 2500) - System.currentTimeMillis()) + "ms IsPacketTryDown?: " + ((lastTryPacketSent + 2500) - System.currentTimeMillis() >= 0));
		System.out.println("UP: " + getBytePerSecondSent() + "bs/s DOWN: " + getBytePerSecondReceived() + "bs/s");
		if (sendingPacket != null) {
			System.out.println("CurrentSending: " + sendingPacket.getClass() + " hace  " + (sendingPacket.getCurrent() - System.currentTimeMillis()) + "ms");
		}
	}
	public void onErrorSend() {}
	public boolean hasReconnectKey() {
		return reconnectKey != null && !reconnectKey.isEmpty();
	}
	public boolean checkReconnectKey(String key) {
		return hasReconnectKey() && key != null && MessageDigest.isEqual(reconnectKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
	}
	public long getBytePerSecondSent() {
		return bytesPerSecondSent;
	}
	public long getBytePerSecondReceived() {
		return bytesPerSecondReceived;
	}
	public long getBytesSent() {
		return bytesSent;
	}
	public long getBytesReceived() {
		return bytesReceived;
	}
	public long getMaxPacketSizeSend() {
		return maxPacketSizeSend;
	}
	private long getMaxBytesSenderOnFiles() {
		long max = -1;
		for (FileSender sender : filesSending.values()) {
			if (!sender.isFinished() && sender.getBytesPerPacket() > max) {
				max = sender.getBytesPerPacket();
			}
		}
		return max;
	}
	public long getMaxPacketSizeReceive() {
		return maxPacketSizeReceive;
	}
	private long getMaxBytesReceiverOnFiles() {
		long max = -1;
		for (FileReceiver receiver : filesReceiver.values()) {
			if (!receiver.isFinished() && receiver.getBytesPerPacket() > max) {
				max = receiver.getBytesPerPacket();
			}
		}
		return max;
	}
	private synchronized void checkNeedUpdateBytesPerSecond() {
		if ((lastBytePerSecondUpdate + 1000) - System.currentTimeMillis() <= 0) {
			bytesPerSecondReceived = newbytesPerSecondReceived;
			newbytesPerSecondReceived = 0;
			bytesPerSecondSent = newbytesPerSecondSent;
			newbytesPerSecondSent = 0;
			lastBytePerSecondUpdate = System.currentTimeMillis();
		}
	}
	private void addPendingPacket(long time, Packet packet) {
		while (pendentingSendPacket.putIfAbsent(time, packet) != null) {
			time++;
		}
	}
	public void deletePendingPacket(Packet packet) {
		for (Entry<Long, Packet> entry : pendentingSendPacket.entrySet()) {
			if (entry.getValue() == packet) {
				pendentingSendPacket.remove(entry.getKey(), packet);
				break;
			}
		}
	}
	public void sendPacket(Packet packet) {
		if (packet == null) {
			return;
		}
		synchronized (sendLock) {
			if (!disconnected) {
				if (pendentingSendPacket.containsValue(packet)) {
					return;
				}
				if (packet.hasAwaitAnswerCallback()) {
					awaitAnswers.putIfAbsent(packet.getAwaitAnswerCallback().getID(), packet.getAwaitAnswerCallback());
				}
				addPendingPacket(System.currentTimeMillis(), packet);
			}
		}
		if (disconnected) {
			failPacket(packet, new IOException("La conexión está cerrada"));
			return;
		}
		sendNextPacket();
	}
	private void sendNextPacket() {
		while (true) {
			Packet packet;
			synchronized (sendLock) {
				if (sendingPacket != null || disconnected || reconnecting || !isConnected() || pendentingSendPacket.isEmpty()) {
					return;
				}
				packet = pendentingSendPacket.firstEntry().getValue();
				sendingPacket = packet;
			}
			try {
				if (send(packet)) {
					return;
				}
			} catch (WritePacketException | WritePendingException exception) {
				failPacket(packet, exception);
			}
		}
	}
	protected void startRead() {
		synchronized (sendLock) {
			if (reading || disconnected) {
				return;
			}
			reading = true;
		}
		readNextPacket();
	}
	public void log(String message) {}
	private void stopRead() {
		reading = false;
	}
	private boolean send(final Packet packet) throws WritePacketException, WritePendingException {
		final AsynchronousSocketChannel channel = connection;
		synchronized (sendLock) {
			if (sendingPacket != null && sendingPacket != packet) {
				return true;
			}
			sendingPacket = packet;
		}
		try {
			int packetID = packetManager.getPacketID(packet.getClass());
			if (packetID == 0) {
				failPacket(packet, new IOException("El paquete no está registrado"));
				return false;
			}
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			PacketUtilities.writeInteger(packetID, output);
			PacketUtilities.writeInteger(packet.getPacketToClientType(), output);
			PacketUtilities.writeLong(System.currentTimeMillis(), output);
			PacketUtilities.writeInteger(packet.hasAwaitAnswerCallback() ? packet.getAwaitAnswerCallback().getID() : packet.getPendentingAnswer(), output);
			ByteArrayOutputStream payload = new ByteArrayOutputStream();
			packet.deserialize(payload);
			output.write(payload.toByteArray(), 0, payload.size());
			if ((long) output.size() + 8 > maxPacketSizeSend || output.size() > MAX_FRAME_BYTES) {
				failPacket(packet, new IOException("El paquete supera el límite de envío de " + maxPacketSizeSend + " bytes"));
				return false;
			}
			ByteArrayOutputStream size = new ByteArrayOutputStream();
			PacketUtilities.writeInteger(output.size(), size);
			final ByteBuffer header = ByteBuffer.allocate(8);
			header.put(size.toByteArray());
			header.rewind();
			final ByteBuffer body = ByteBuffer.wrap(output.toByteArray());
			lastTryPacketSent = System.currentTimeMillis();
			packet.setCurrent(lastTryPacketSent);
			if (packet.hasAwaitAnswerCallback()) {
				packet.getAwaitAnswerCallback().startWaiting();
			}
			writeFully(channel, header, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
				@Override
				public void completed(Integer result, AsynchronousSocketChannel attachment) {
					writeFully(channel, body, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
						@Override
						public void completed(Integer count, AsynchronousSocketChannel socket) {
							sent(packet);
						}
						@Override
						public void failed(Throwable exception, AsynchronousSocketChannel socket) {
							writeFailed(channel, packet, exception);
						}
					});
				}
				@Override
				public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
					writeFailed(channel, packet, exception);
				}
			});
			return true;
		} catch (WritePacketException | RuntimeException exception) {
			failPacket(packet, exception);
			return false;
		}
	}
	private void writeFully(final AsynchronousSocketChannel channel, final ByteBuffer buffer,
			final CompletionHandler<Integer, AsynchronousSocketChannel> callback) {
		if (channel != connection || disconnected) {
			return;
		}
		try {
			channel.write(buffer, channel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
				@Override
				public void completed(Integer count, AsynchronousSocketChannel attachment) {
					if (channel != connection || disconnected) {
						return;
					}
					if (count < 0) {
						callback.failed(new IOException("Fin de conexión durante el envío"), channel);
						return;
					}
					recordBytes(true, count);
					if (buffer.hasRemaining()) {
						writeFully(channel, buffer, callback);
					} else {
						callback.completed(count, channel);
					}
				}
				@Override
				public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
					callback.failed(exception, channel);
				}
			});
		} catch (RuntimeException exception) {
			callback.failed(exception, channel);
		}
	}
	private void writeFailed(AsynchronousSocketChannel channel, Packet packet, Throwable failure) {
		if (channel == connection && !disconnected) {
			Exception exception = failure instanceof Exception ? (Exception) failure : new IOException("Error de escritura", failure);
			failPacket(packet, exception);
			dropAndReconnect();
		}
	}
	private void failPacket(Packet packet, Exception exception) {
		synchronized (sendLock) {
			deletePendingPacket(packet);
			if (sendingPacket == packet) {
				sendingPacket = null;
			}
		}
		try {
			if (packet.hasAwaitAnswerCallback()) {
				AwaitAnswerCallback callback = packet.getAwaitAnswerCallback();
				awaitAnswers.remove(callback.getID(), callback);
				callback.fail(exception);
			}
		} catch (RuntimeException callbackFailure) {
			log("Error en el callback de respuesta");
		}
		try {
			if (packet.hasSentCallback()) {
				packet.getSentCallback().fail(exception);
			}
			onErrorSend();
			log(exception.getMessage());
		} catch (RuntimeException callbackFailure) {
			log("Error en el callback de envío");
		}
	}
	private void sent(Packet packet) {
		synchronized (sendLock) {
			deletePendingPacket(packet);
			sendingPacket = null;
			lastTryPacketSent = 0;
		}
		packetsent++;
		lastPacketSent = System.currentTimeMillis();
		try {
			if (packet.hasSentCallback()) {
				packet.getSentCallback().complete(lastPacketSent - packet.getCurrent());
			}
		} catch (RuntimeException callbackFailure) {
			log("Error en el callback de envío");
		} finally {
			if (packet instanceof PacketGlobalDisconnect) {
				canReconnect = false;
				totalDisconnect();
			} else {
				sendNextPacket();
			}
		}
	}
	private void readNextPacket() {
		final AsynchronousSocketChannel channel = connection;
		final ByteBuffer header = ByteBuffer.allocate(8);
		readFully(channel, header, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
			@Override
			public void completed(Integer count, AsynchronousSocketChannel attachment) {
				try {
					nextPacketSize = PacketUtilities.getInteger(new ByteArrayInputStream(header.array()));
					if (nextPacketSize <= 0 || nextPacketSize > MAX_FRAME_BYTES || (long) nextPacketSize + 8 > maxPacketSizeReceive) {
						log("Tamaño de paquete recibido inválido");
						totalDisconnect();
						return;
					}
					final ByteBuffer body = ByteBuffer.allocate(nextPacketSize);
					readFully(channel, body, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
						@Override
						public void completed(Integer size, AsynchronousSocketChannel socket) {
							read(body);
						}
						@Override
						public void failed(Throwable exception, AsynchronousSocketChannel socket) {
							readFailed(channel);
						}
					});
				} catch (ReadPacketException | RuntimeException exception) {
					totalDisconnect();
				}
			}
			@Override
			public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
				readFailed(channel);
			}
		});
	}
	private void readFully(final AsynchronousSocketChannel channel, final ByteBuffer buffer,
			final CompletionHandler<Integer, AsynchronousSocketChannel> callback) {
		if (channel != connection || disconnected || !reading) {
			return;
		}
		try {
			channel.read(buffer, channel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
				@Override
				public void completed(Integer count, AsynchronousSocketChannel attachment) {
					if (channel != connection || disconnected || !reading) {
						return;
					}
					if (count < 0) {
						callback.failed(new IOException("Fin de conexión durante la lectura"), channel);
						return;
					}
					recordBytes(false, count);
					if (buffer.hasRemaining()) {
						readFully(channel, buffer, callback);
					} else {
						callback.completed(count, channel);
					}
				}
				@Override
				public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
					callback.failed(exception, channel);
				}
			});
		} catch (RuntimeException exception) {
			callback.failed(exception, channel);
		}
	}
	private void readFailed(AsynchronousSocketChannel channel) {
		if (channel == connection && !disconnected) {
			dropAndReconnect();
		}
	}

	private void read(ByteBuffer bufPacket) {
		ByteArrayInputStream input = new ByteArrayInputStream(bufPacket.array());
		int packetID = 0;
		long current = 0;
		try {
			if (byteDebug) {
				List<Byte> testList = new ArrayList<Byte>();
				for (byte a : bufPacket.array()) {
					testList.add(a);
				}
				log("Packet Received (" + testList.size() + ") " + testList);	
			}
			packetID = PacketUtilities.getInteger(input);
			int cliendID = PacketUtilities.getInteger(input);
			current = PacketUtilities.getLong(input);
			int awaitAnswer = PacketUtilities.getInteger(input);
			Packet packet = null;
			try {
				packet = packetManager.getPacket(packetID, input);
			} catch (ReadPacketException e) {
				System.out.println("Error al leer el packet (" + packetID + ") packetSize" + bufPacket.array().length); 
				String exception = e.getCause() + "\n";
				for (StackTraceElement stack : e.getStackTrace()) {
					exception = exception + "        at " + stack.getClassName() + "." + stack.getMethodName() + " (" + stack.getFileName() + ":" + stack.getLineNumber() + ")\n";
				}
				System.err.println(exception);
			}
			if (packet != null) {
				packetReceived++;
				if (debugMode) {
					System.out.println("Packet recibido: " + packet.getClass().getName());
				}
				packet.setPendentingAnswer(awaitAnswer);
				packet.setCurrent(current);
				downPing = System.currentTimeMillis() - packet.getCurrent();
				AwaitAnswerCallback awaitAnswerCallback = awaitAnswers.get(awaitAnswer);
				if (awaitAnswer != 0 && awaitAnswerCallback != null && awaitAnswerCallback.isCorrectPacket(packet)
						&& awaitAnswers.remove(awaitAnswer, awaitAnswerCallback)) {
					awaitAnswerCallback.complete(packet);
				} else {
					if (packet.getPacketToClientType() == 0) {
						if (packet instanceof PacketGlobalAskNewConnection) {
							if (!connected) {
								connected = true;						
							}
						} else if (packet instanceof PacketGlobalReconnectDefineKey) {
							PacketGlobalReconnectDefineKey defineKey = (PacketGlobalReconnectDefineKey) packet;
							reconnectKey = defineKey.getKey();
							connected = true;
							onConnect();
						} else if (packet instanceof PacketGlobalDisconnect) {
							System.out.println("Disconnected by " + (clientConnectionType == ClientConnectionType.CLIENT_TO_SERVER ? "server" : "client") );
							if (clientConnectionType == ClientConnectionType.SERVER_TO_CLIENT) {
								canReconnect = false;
								sendPacket(packet);
							} else {
								totalDisconnect();
							}
						} else if (packet instanceof PacketGlobalKick) {
							if (clientConnectionType == ClientConnectionType.CLIENT_TO_SERVER) {
								System.out.println("Kicked by server");
								disconnect();
							}
						} else if (packet instanceof PacketGlobalPing) {
							lastPinged = System.currentTimeMillis();
							if (clientConnectionType == ClientConnectionType.SERVER_TO_CLIENT) {
								sendPacket(packet);
							} else {
								//System.out.println("Ping: " + (System.currentTimeMillis() - packet.getCurrent()) + "ms up-" + bytesPerSecondsent + " down-" + bytesPerSecondReceived);
							}
							onPing();
						} else if (handleFilePacket(packet)) {
						} else if (packet instanceof PacketVoiceChatReceive) {
							PacketVoiceChatReceive vcReceive = (PacketVoiceChatReceive) packet;
							earn(vcReceive);
						} else if (packet instanceof PacketVoiceChatSend) {
							PacketVoiceChatSend vcSend = (PacketVoiceChatSend) packet;
							onSpeak(vcSend);
						}
					} else {
						onRecibe(packet);
					}
				}
			} else {
				System.err.println("Packet recibido con ID " + packetID + " no registrado...");
			}
		} catch (ReadPacketException | RuntimeException exception) {
			log("Error al procesar el paquete recibido");
			totalDisconnect();
		} finally {
			nextPacketSize = 0;
			if (reading) {
				if (clientConnectionType.equals(ClientConnectionType.SERVER_TO_CLIENT)) { 
					// si es conexion servidor-cliente y no tiene generada la reconnectkey (es un cliente pendiente, por lo que solo lee un paquete) 
					if (hasReconnectKey()) {
						readNextPacket();
					}
				} else { // si es cliente-servidor lee todo
					readNextPacket();
				}
			}
		}
	}
	public void onReceibeFilePart(FileReceiver fileReceiver) {}
	public String sendFile(String path, String dest) {
		return sendFile(path, dest, Utils.getFromSacledBytes("1MB"));
	}
	public String sendFile(String path, String dest, long bytesPerPacket) {
		return sendFile(new FileSender(getRandomFileCode(), path, bytesPerPacket) {
			@Override
			public void onReject() {}
			@Override
			public void onStart() {}
			@Override
			public void onFilePartSent(int porcent, long bytesSent, long fileLenght) {}
			@Override
			public void onFinish() {}			
		}, dest);
	}
	public String sendFile(FileSender fileSender, String dest) {
		if (fileSender.getCode() == null || fileSender.getCode().isEmpty()) {
			fileSender.setCode(getRandomFileCode());
		}
		if (fileSender.getBytesPerPacket() <= 0 || fileSender.getBytesPerPacket() > FileSender.MAX_BYTES_PER_PACKET || disconnected || reconnecting) {
			fileSender.onReject();
			return null;
		}
		if (filesSending.putIfAbsent(fileSender.getCode(), fileSender) == null) {
			sendPacket(new PacketFileCanSent(fileSender.getCode(), fileSender.getFileType(), dest, fileSender.getBytesPerPacket(), fileSender.getFileLenght()));
			return fileSender.getCode();
		} else {
			return null;
		}
	}
	public void changeBytesPerPacketOfSender(String code, long bytes) {
		sendPacket(new PacketFileCanChangeBytesPerPacket(code, bytes));
	}
	private String getRandomFileCode() {
		String result = "";
		final char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890".toCharArray();
		for (int i = 0; i < 10; i++) {
			char c = chars[Utils.random.nextInt(chars.length)];
			result = result + (Character.isLetter(c) ? Utils.random.nextBoolean() ? Character.toLowerCase(c) : c : c);
		}
		if (filesReceiver.containsKey(result) || filesSending.containsKey(result)) {
			return getRandomFileCode();
		} else {
			return result;
		}
	}
	public void reconnect(AsynchronousSocketChannel channel) {
		synchronized (sendLock) {
			if (disconnected) {
				try {
					channel.close();
				} catch (IOException exception) {
					log("Error al cerrar una reconexión");
				}
				return;
			}
			connection = channel;
			connectionCompleted = true;
			reconnecting = false;
			sendingPacket = null;
			lastPinged = System.currentTimeMillis();
		}
		if (reconnectThread != null) {
			reconnectThread.kill();
		}
		startRead();
		onReconnect();
		sendNextPacket();
	}
	public void dropAndReconnect() {
		synchronized (sendLock) {
			if (disconnected || reconnecting) {
				return;
			}
			reconnecting = true;
			reading = false;
			connectionCompleted = false;
			sendingPacket = null;
		}
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (IOException exception) {
			log("Error al cerrar el canal interrumpido");
		}
		clearFileTransfers();
		if (!hasReconnectKey() || !canReconnect || !connected) {
			totalDisconnect();
			return;
		}
		try {
			onDropCanReconnect();
		} catch (RuntimeException exception) {
			totalDisconnect();
			return;
		}
		reconnectThread = new StoppableThread() {
			@Override
			public void run() {
				if (clientConnectionType == ClientConnectionType.CLIENT_TO_SERVER) {
					reconnectClient();
				} else {
					try {
						sleep(30000);
						totalDisconnect();
					} catch (InterruptedException exception) {
						interrupt();
					}
				}
			}
		};
		reconnectThread.start();
	}
	public void disconnect() {
		if (!disconnected && !reconnecting && isConnected()) {
			sendPacket(new PacketGlobalDisconnect());
		} else {
			totalDisconnect();
		}
		
	}
	private void totalDisconnect() {
		totalDisconnect(true);
	}
	private void totalDisconnect(boolean notify) {
		List<Packet> pending;
		synchronized (sendLock) {
			if (disconnected) {
				return;
			}
			disconnected = true;
			connected = false;
			connectionCompleted = false;
			reconnecting = false;
			reading = false;
			pending = new ArrayList<Packet>(pendentingSendPacket.values());
			pendentingSendPacket.clear();
			sendingPacket = null;
		}
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (IOException exception) {
			log("Error al cerrar la conexión");
		}
		IOException failure = new IOException("La conexión está cerrada");
		for (Packet packet : pending) {
			failPacket(packet, failure);
		}
		for (Entry<Integer, AwaitAnswerCallback> entry : awaitAnswers.entrySet()) {
			AwaitAnswerCallback callback = entry.getValue();
			if (awaitAnswers.remove(entry.getKey(), callback)) {
				try {
					callback.fail(failure);
				} catch (RuntimeException exception) {
					log("Error en el callback de desconexión");
				}
			}
		}
		synchronized (this) {
			for (FileReceiver receiver : filesReceiver.values()) {
				receiver.close();
			}
			filesReceiver.clear();
			List<FileSender> senders = new ArrayList<FileSender>(filesSending.values());
			filesSending.clear();
			for (FileSender sender : senders) {
				try {
					sender.onReject();
				} catch (RuntimeException exception) {
					log("Error al cancelar un archivo");
				}
			}
		}
		if (micThread != null) {
			micThread.kill();
			micThread = null;
		}
		for (AudioChannel channel : audioChannels.values()) {
			channel.kill();
		}
		audioChannels.clear();
		if (timeOutThread != null) {
			timeOutThread.kill();
		}
		if (reconnectThread != null) {
			reconnectThread.kill();
		}
		if (notify) {
			try {
				onDisconnect();
			} catch (RuntimeException exception) {
				log("Error en el callback de desconexión");
			}
		}
	}
	public boolean isConnected() {
		return !disconnected && connection != null && connection.isOpen() && connectionCompleted;
	}
	public long getDownPing() {
		return downPing;
	}
	public String getIP() {
		return ipC;
	}
	public boolean isReconnecting() {
		return reconnecting;
	}
	public long getTimeOutTime() {
		return timeOut;
	}
	public void setTimeOutTime(long time) {
		if (time >= 2000) {
			timeOut = time;
		} else {
			throw new IllegalArgumentException("The time of time out need >= 2000");
		}
	}
	public int getPacketSent() {
		return packetsent;
	}
	public int getPacketReceived() {
		return packetReceived;
	}
	public void onPing() {}
	public boolean canReceiveFile(String dest, String fileType, long bytesPerPacket, long fileLenght) {
		return false;
	}
	public boolean canChangeBytesPerPacket(String code, String filePath, long bytes) {
		return true;
	}
	public void onReceiveFile(String dest, String fileType, long fileLenght) {

	}
	public boolean isCanEarn() {
		return canEarn;
	}
	public void setCanEarn(boolean canEarn) {
		this.canEarn = canEarn;
	}
	public boolean isCanSpeak() {
		return canSpeak;
	}
	public void setCanSpeak(boolean canSpeak) {
		this.canSpeak = canSpeak;
	}
	public boolean canEarn(int channelID) {
		return false;
	}
	public void earn(PacketVoiceChatReceive packet) {
		if (canEarn && canEarn(packet.getChannelID())) {
			if (!audioChannels.containsKey(packet.getChannelID())) {
				audioChannels.put(packet.getChannelID(), new AudioChannel(packet.getChannelID()));
				audioChannels.get(packet.getChannelID()).start();
				System.out.println("New audio channel openned " + packet.getChannelID());
			}
			audioChannels.get(packet.getChannelID()).addToQueue(packet);
		}
	}
	public void speak(PacketVoiceChatSend packet) {
		if (canSpeak) {
			sendPacket(packet);
		}
	}
	public boolean isMicrophoneOpenned() {
		return micThread != null;
	}
	public void openMicrophone() throws LineUnavailableException {
		if (!isMicrophoneOpenned()) {
			micThread = new MicrophoneThread() {
				@Override
				public void send(PacketVoiceChatSend packet) {
					sendPacket(packet);
				}				
			};
			micThread.start();
		}
	}
	/*
	 * En la parte del servidor deberemos definir la ID del canal, recomiendo
	 * que sea la ID del cliente...
	 */
	public int getChannelID() { // SOLO SE USARA EN EL SERVIDOR
		return -1;
	}
	/*
	 * En la parte del servidor deberemos enviar este packet al resto de clientes
	 * Antes deberemos de pasarlo a PacketVoiceChatReceive con el metodo que está
	 * integrado dentro de la clase del paquete toSend(int channelID)
	 * La ID del canal será la misma ID del cliente.
	 */
	public void onSpeak(PacketVoiceChatSend packet) {}
	public int getNextAwaitAnswerID() {
		int id;
		do {
			int sequence = answerCounter.getAndIncrement() & Integer.MAX_VALUE;
			id = clientConnectionType == ClientConnectionType.SERVER_TO_CLIENT ? -sequence : sequence;
		} while (id == 0 || awaitAnswers.containsKey(id));
		return id;
	}
	private void checkTimeOutAwaitAnswers() {
		ArrayList<AwaitAnswerCallback> cloned = new ArrayList<AwaitAnswerCallback>(awaitAnswers.values());
		for (AwaitAnswerCallback aac : cloned) {
			if (aac.isTimeOut()) {
				if (awaitAnswers.remove(aac.getID(), aac)) {
					try {
						aac.fail(new IOException("Tiempo de espera agotado"));
					} catch (RuntimeException exception) {
						log("Error en el callback de tiempo de espera");
					}
				}
			}
		}
	}
	protected void killClient() {
		timeOutThread.kill();
		stopRead();
		connection = null;
	}

	private synchronized boolean handleFilePacket(Packet packet) {
		if (packet instanceof PacketFileCanSent) {
			PacketFileCanSent request = (PacketFileCanSent) packet;
			boolean accepted = request.getCode() != null && !request.getCode().isEmpty() && request.getCode().length() <= 128
					&& !filesReceiver.containsKey(request.getCode())
					&& request.getBytesPerPacket() > 0 && request.getBytesPerPacket() <= FileSender.MAX_BYTES_PER_PACKET
					&& request.getFileLenght() >= 0
					&& canReceiveFile(request.getPath(), request.getFileType(), request.getBytesPerPacket(), request.getFileLenght());
			FileReceiver receiver = null;
			if (accepted) {
				receiver = new FileReceiver(request.getCode(), request.getBytesPerPacket(), request.getFileLenght(),
						request.getPath(), getFileReceiveDirectory());
				accepted = receiver.isReady();
				if (accepted && !receiver.isFinished()) {
					filesReceiver.put(request.getCode(), receiver);
				} else if (!accepted) {
					receiver.close();
				}
			}
			refreshFileLimits();
			sendPacket(new PacketFileSentRequest(request.getCode(), accepted));
			if (accepted && receiver.isFinished()) {
				onReceiveFile(receiver.getFilePath(), receiver.getFileType(), receiver.getFileLenght());
			}
		} else if (packet instanceof PacketFileSentRequest) {
			PacketFileSentRequest response = (PacketFileSentRequest) packet;
			FileSender sender = filesSending.get(response.getCode());
			if (sender != null) {
				if (response.canSent()) {
					sender.onStart();
					if (sender.isFinished()) {
						filesSending.remove(sender.getCode(), sender);
						sender.onFinish();
					} else {
						sendNextFilePart(sender);
					}
				} else {
					cancelFile(response.getCode(), false);
				}
			}
		} else if (packet instanceof PacketFilePartOfFile) {
			PacketFilePartOfFile part = (PacketFilePartOfFile) packet;
			FileReceiver receiver = filesReceiver.get(part.getCode());
			if (receiver != null) {
				if (receiver.received(part.getBytes())) {
					sendPacket(new PacketFilePartOfFileReceived(part.getCode(), part.getBytes().length));
					onReceibeFilePart(receiver);
					if (receiver.isFinished()) {
						filesReceiver.remove(receiver.getCode(), receiver);
						refreshFileLimits();
						onReceiveFile(receiver.getFilePath(), receiver.getFileType(), receiver.getFileLenght());
					}
				} else {
					cancelFile(part.getCode(), true);
				}
			}
		} else if (packet instanceof PacketFilePartOfFileReceived) {
			PacketFilePartOfFileReceived response = (PacketFilePartOfFileReceived) packet;
			FileSender sender = filesSending.get(response.getCode());
			if (sender != null) {
				long elapsed = sender.sent(response.getBytesLenght());
				if (elapsed < 0) {
					cancelFile(response.getCode(), true);
				} else {
					sender.onFilePartSent(sender.getPorcentSent(), sender.getSent(), sender.getFileLenght());
					if (sender.isFinished()) {
						filesSending.remove(sender.getCode(), sender);
						refreshFileLimits();
						sender.onFinish();
					} else {
						adaptFilePart(sender, elapsed);
					}
				}
			}
		} else if (packet instanceof PacketFileCancelSend) {
			cancelFile(((PacketFileCancelSend) packet).getCode(), false);
		} else if (packet instanceof PacketFileCanChangeBytesPerPacket) {
			PacketFileCanChangeBytesPerPacket change = (PacketFileCanChangeBytesPerPacket) packet;
			long bytes = change.getBytes();
			FileSender sender = filesSending.get(change.getCode());
			FileReceiver receiver = filesReceiver.get(change.getCode());
			if (bytes > 0 && bytes <= FileSender.MAX_BYTES_PER_PACKET) {
				if (sender != null && canChangeBytesPerPacket(change.getCode(), sender.getFilePath(), bytes)) {
					sender.setBytesPerPacket(bytes);
				} else if (receiver != null && canChangeBytesPerPacket(change.getCode(), receiver.getFilePath(), bytes)) {
					receiver.setBytesPerPacket(bytes);
					refreshFileLimits();
					sendPacket(change);
				}
			}
			refreshFileLimits();
		} else {
			return false;
		}
		return true;
	}

	private void sendNextFilePart(FileSender sender) {
		byte[] bytes = sender.getNextFileBytes();
		if (bytes == null) {
			cancelFile(sender.getCode(), true);
			return;
		}
		refreshFileLimits();
		sendPacket(new PacketFilePartOfFile(sender.getCode(), bytes));
	}

	private void cancelFile(String code, boolean informPeer) {
		FileReceiver receiver = filesReceiver.remove(code);
		if (receiver != null) {
			receiver.close();
		}
		FileSender sender = filesSending.remove(code);
		refreshFileLimits();
		if (informPeer) {
			sendPacket(new PacketFileCancelSend(code));
		}
		if (sender != null) {
			sender.onReject();
		}
	}

	private void refreshFileLimits() {
		maxPacketSizeSend = Math.max(defaultMaxPacketSizeSend, getMaxBytesSenderOnFiles() + 512);
		maxPacketSizeReceive = Math.max(defaultMaxPacketSizeReceive, getMaxBytesReceiverOnFiles() + 512);
	}

	public File getFileReceiveDirectory() {
		return new File("").getAbsoluteFile();
	}

	private void adaptFilePart(final FileSender sender, long elapsed) {
		long target = sender.getBytesPerPacket();
		if (elapsed > 0 && elapsed < maxTimePerPacketOnSendFiled && !lastPacketSendFileChangedByteAmount) {
			target = (long) (target * ((double) maxTimePerPacketOnSendFiled / elapsed));
		} else if (elapsed >= maxTimePerPacketOnSendFiled + 150) {
			target = (long) (target * ((double) (maxTimePerPacketOnSendFiled + 150) / elapsed));
		}
		target = Math.max(1024, Math.min(FileSender.MAX_BYTES_PER_PACKET, target));
		if (target == sender.getBytesPerPacket()) {
			lastPacketSendFileChangedByteAmount = false;
			sendNextFilePart(sender);
			return;
		}
		final long requestedBytes = target;
		lastPacketSendFileChangedByteAmount = true;
		// El siguiente fragmento espera la confirmación del nuevo tamaño.
		sendPacket(new PacketFileCanChangeBytesPerPacket(sender.getCode(), requestedBytes,
				new AwaitAnswerCallback(this, PacketFileCanChangeBytesPerPacket.class) {
			@Override
			public void onAnswer(Packet packet) {
				PacketFileCanChangeBytesPerPacket response = (PacketFileCanChangeBytesPerPacket) packet;
				if (filesSending.get(sender.getCode()) == sender && sender.getCode().equals(response.getCode())
						&& response.getBytes() == requestedBytes) {
					sender.setBytesPerPacket(requestedBytes);
					sendNextFilePart(sender);
				} else {
					cancelFile(sender.getCode(), true);
				}
			}
			@Override
			public void onTimeOut() {
				cancelFile(sender.getCode(), true);
			}
		}));
	}

	private void reconnectClient() {
		for (int attempt = 0; attempt < 3 && !disconnected; attempt++) {
			AsynchronousSocketChannel channel = null;
			try {
				Thread.sleep(attempt * 3000L);
				if (disconnected) {
					return;
				}
				channel = AsynchronousSocketChannel.open();
				channel.connect(new InetSocketAddress(ip, port)).get(5, TimeUnit.SECONDS);
				synchronized (sendLock) {
					if (disconnected) {
						channel.close();
						return;
					}
					connection = channel;
					connectionCompleted = true;
				}
				PacketGlobalReconnect packet = new PacketGlobalReconnect(reconnectKey);
				packet.setSentCallback(new PacketSentCallback() {
					@Override
					public void onSent(long milliseconds) {
						reconnecting = false;
						lastPinged = System.currentTimeMillis();
						startRead();
						onReconnect();
						sendNextPacket();
					}
					@Override
					public void onError(Exception exception) {
						totalDisconnect();
					}
				});
				send(packet);
				return;
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception exception) {
				if (channel != null) {
					try {
						channel.close();
					} catch (IOException closeFailure) {
						log("Error al cerrar un intento de conexión");
					}
				}
			}
		}
		totalDisconnect();
	}

	private synchronized void clearFileTransfers() {
		for (FileReceiver receiver : filesReceiver.values()) {
			receiver.close();
		}
		filesReceiver.clear();
		List<FileSender> senders = new ArrayList<FileSender>(filesSending.values());
		filesSending.clear();
		for (Packet packet : new ArrayList<Packet>(pendentingSendPacket.values())) {
			if (packet instanceof PacketFileCanSent || packet instanceof PacketFilePartOfFile
					|| packet instanceof PacketFilePartOfFileReceived || packet instanceof PacketFileSentRequest
					|| packet instanceof PacketFileCancelSend || packet instanceof PacketFileCanChangeBytesPerPacket) {
				failPacket(packet, new IOException("Transferencia interrumpida"));
			}
		}
		refreshFileLimits();
		for (FileSender sender : senders) {
			try {
				sender.onReject();
			} catch (RuntimeException exception) {
				log("Error al cancelar un archivo");
			}
		}
	}

	private synchronized void recordBytes(boolean sent, int count) {
		checkNeedUpdateBytesPerSecond();
		if (sent) {
			newbytesPerSecondSent += count;
			bytesSent += count;
		} else {
			newbytesPerSecondReceived += count;
			bytesReceived += count;
		}
	}
}
