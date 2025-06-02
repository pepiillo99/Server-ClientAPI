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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.WritePendingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
import me.pepe.ServerClientAPI.Utils.Utils;
import me.pepe.ServerClientAPI.Utils.File.FileReceiver;
import me.pepe.ServerClientAPI.Utils.File.FileSender;
import me.pepe.ServerClientAPI.VoiceChat.AudioChannel;
import me.pepe.ServerClientAPI.VoiceChat.MicrophoneThread;

public abstract class ClientConnection {
	private ServerClientAPI packetManager;
	private AsynchronousSocketChannel connection;
	private String ip;
	private int port;
	private ClientConnectionType clientConnectionType = null;
	private List<Packet> pendentingSendPacket = new ArrayList<Packet>();
	private long maxPacketSizeSend = Utils.getFromSacledBytes("5KB");
	private long defaultMaxPacketSizeSend = maxPacketSizeSend;
	private long maxPacketSizeReceive = Utils.getFromSacledBytes("5KB");
	private long defaultMaxPacketSizeReceive = maxPacketSizeReceive;
	private int nextPacketSize;
	private long downPing = 0;
	private boolean reading = false;
	private ByteBuffer readIncompletePacket;
	private ByteBuffer writeIncompletePacket;
	private String ipC = "";
	public boolean debugMode = false;
	private boolean connectionCompleted = false; // first connection is completed?
	private long lastTryPacketSent = 0;
	private long lastPacketSent = 0;
	private long lastBytePerSecondUpdate = 0; // la ultima vez que actualizá los bytes por segundo
	// b/s Received
	private long bytesPerSecondReceived = 0;
	private long newbytesPerSecondReceived = 0;	
	// b/s sent
	private long bytesPerSecondSent = 0;
	private long newbytesPerSecondSent = 0;
	private long bytesSent = 0;
	private long bytesReceived = 0;
	public boolean byteDebug = false;
	private Packet sendingPacket = null;
	private boolean reconnecting = false;
	private String reconnectKey = "";
	private long timeOut = 5000;
	private long lastPinged = 0;
	private Thread timeOutThread;
	private int packetsent = 0;
	private int packetReceived = 0;
	private boolean canReconnect = true;
	private int reconnectAttempts = 0;
	private boolean connected = false; // para saber si al desconectar puede reconectar o definitivamente se desconecto
	private Thread reconnectThread; // thread que define si la conexion caduca en el servidor
	private long maxTimePerPacketOnSendFiled = 500;
	private boolean lastPacketSendFileChangedByteAmount = false;
	private HashMap<String, FileSender> filesSending = new HashMap<String, FileSender>();
	private HashMap<String, FileReceiver> filesReceiver = new HashMap<String, FileReceiver>();
	// voice chat
	private boolean canEarn = false;
	private boolean canSpeak = false;
	private MicrophoneThread micThread;
	private HashMap<Integer, AudioChannel> audioChannels = new HashMap<Integer, AudioChannel>();
	//
	private int answerCounter = 1;
	private HashMap<Integer, AwaitAnswerCallback> awaitAnswers = new HashMap<Integer, AwaitAnswerCallback>();
	public ClientConnection(AsynchronousSocketChannel connection, ServerClientAPI packetManager) {
		this(connection, packetManager, true, true);
	}
	public ClientConnection(AsynchronousSocketChannel connection, ServerClientAPI packetManager, boolean startRead) {
		this(connection, packetManager, true, true);
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
			reconnectKey = new BigInteger(25, Utils.random).toString(32);
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
			timeOutThread = new Thread() {
				@Override
				public void run() {
					while (true) {
						try {
							sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (isConnected() && !reconnecting && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
							System.out.println("Client connection time out! " + reconnectKey);
							dropAndReconnect();
						}
					}
				}
			};
		} else {
			timeOutThread = new Thread() {
				@Override
				public void run() {
					try {
						sleep(1000);
						if (!hasReconnectKey()) {
							System.out.println("Pendenting connection dropped by timeout");
							totalDisconnect();
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}}
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
		timeOutThread.start();
	}
	public ClientConnection(String ip, int port, ServerClientAPI packetManager) throws IOException {
		this.packetManager = packetManager;
		this.ip = ip;
		this.port = port;
		connection = AsynchronousSocketChannel.open();
		this.clientConnectionType = ClientConnectionType.CLIENT_TO_SERVER;
		timeOutThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (isConnected()) {
						try {
							sendPacket(new PacketGlobalPing());
							checkTimeOutAwaitAnswers();
						} catch (NotYetConnectedException ex) {
							onFailedConnect();
							dropAndReconnect();
						}
					}
					if (isConnected() && !reconnecting && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
						System.out.println("Server connection time out! " + reconnectKey);
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
					timeOutThread.start();
					startRead();
				}
				@Override
				public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
					System.out.println("Error al conectar con el servidor " + ip + ":" + port);
					onFailedConnect();
				}
			});
		} catch (UnresolvedAddressException ex) {
			onFailedConnect();
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
		return reconnectKey.equals(key);
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
	private void setMaxPacketSizeSend(long maxPacketSize) {
		long max = getMaxBytesSenderOnFiles();
		if (maxPacketSize < max) {
			this.maxPacketSizeSend = max;
		} else {
			this.maxPacketSizeSend = maxPacketSize;
		}
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
	private void restartMaxPacketSizeReceive() {
		this.maxPacketSizeReceive = defaultMaxPacketSizeReceive;
		System.out.println("Max packet size to receive restarted to " + Utils.getBytesScaled(maxPacketSizeSend));
	}
	public long getMaxPacketSizeReceive() {
		return maxPacketSizeReceive;
	}
	private void setMaxPacketSizeReceive(long maxPacketSize) {
		long max = getMaxBytesReceiverOnFiles();
		if (maxPacketSize < max) {
			this.maxPacketSizeReceive = max;
		} else {
			this.maxPacketSizeReceive = maxPacketSize;
		}
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
	private void restartMaxPacketSizeSend() {
		this.maxPacketSizeSend = defaultMaxPacketSizeSend;
		System.out.println("Max packet size to send restarted to " + Utils.getBytesScaled(maxPacketSizeSend));
	}
	private void checkNeedUpdateBytesPerSecond() {
		if ((lastBytePerSecondUpdate + 1000) - System.currentTimeMillis() <= 0) {
			bytesPerSecondReceived = newbytesPerSecondReceived;
			newbytesPerSecondReceived = 0;
			bytesPerSecondSent = newbytesPerSecondSent;
			newbytesPerSecondSent = 0;
			lastBytePerSecondUpdate = System.currentTimeMillis();
		}
	}
	public void sendPacket(Packet packet) {
		if (debugMode) {
			System.out.println("Enviando packet " + packet.getClass().getName());
		}
		if (!isConnected() || reconnecting) { // es probable que se este reconectando?
			if (!packet.isIgnorable()) {
				pendentingSendPacket.add(packet);
			}
		} else if (pendentingSendPacket.isEmpty()) {
			pendentingSendPacket.add(packet);
			try {
				lastTryPacketSent = System.currentTimeMillis();
				if (byteDebug) {
					log("send normal");
				}
				if (debugMode) {
					System.out.println("enviando...");
				}
				send(packet);
			} catch (WritePacketException | WritePendingException e) {
				System.out.println("Error al enviar el packet " + packet.getClass().getName() + " intentandolo denuevo en 100 ms...");
				tryResend(packet);	
			}
		} else {
			pendentingSendPacket.add(packet);
			if (sendingPacket == null && lastTryPacketSent == 0) {
				System.out.println("Se intentará forzar el envio del packet...");
				try {
					send(packet);
					System.out.println("Packet enviado de forma forzada...");
				} catch (WritePacketException | WritePendingException e) {
					System.out.println("No se pudo enviar el packet " + packet.getClass().getName() + ", probablemente está escribiendo un packet... Packets pendientes por enviar: " + pendentingSendPacket.size());
				}
			} else {
				if ((lastTryPacketSent + 2500) - System.currentTimeMillis() <= 0) {
					onErrorSend();
					System.out.println("Hace mas de 2 segundos y medio que se envio el ultimo packet. ¿El proceso de send esta parado? forzando el envio del packet pendiente");
					if (sendingPacket != null) {
						System.out.println("Intentando enviar el packet: " + sendingPacket.getClass().getName());
					} else {
						System.out.println("El packet pendiente parta enviar es nullo...");
					}
					System.out.println(lastTryPacketSent + " - " + System.currentTimeMillis() + " - " + ((lastTryPacketSent + 2500) - System.currentTimeMillis())  + " - " + ((lastTryPacketSent + 2500) - System.currentTimeMillis() <= 0));
					try {
						if (byteDebug) {
							log("send by error?");
						}
						send(packet);
					} catch (WritePacketException | WritePendingException e) {
						System.out.println("No se pudo enviar el packet " + packet.getClass().getName() + ", probablemente está escribiendo un packet... Packets pendientes por enviar: " + pendentingSendPacket.size());
					}
				} else {
					if (debugMode) {
						System.out.println("Hay " + pendentingSendPacket.size() + " packets pendientes, ya hay uno enviandose? " + sendingPacket + " " + lastTryPacketSent);
					}
				}
			}
		}
	}
	private void tryResend(Packet packet) {
		try {
			onErrorSend();
			System.out.println("duermiendo 100 ms...");
			Thread.sleep(100);
			System.out.println("intentando...");
			try {
				debugMode = true;
				if (byteDebug) {
					log("send by tryresend");
				}
				send(packet);
				System.out.println("Packet correctamente enviado");
			} catch (WritePacketException | WritePendingException e) {
				System.out.println("Denuevo fallo el enviar el packet " + packet.getClass().getName() + " intentandolo denuevo en 100 ms...");
				tryResend(packet);
			}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	protected void startRead() {
		if (!reading) {
			reading = true;
			readNextPacket();
		}
	}
	private void stopRead() {
		reading = false;
	}
	public void log(String s) {}
	private void send(Packet packet) throws WritePacketException, WritePendingException {
		if (sendingPacket == null) {
			sendingPacket = packet;
			try {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ByteArrayOutputStream packetOutput = new ByteArrayOutputStream();
				packet.deserialize(packetOutput);
				if (packetOutput.size() + 8 < maxPacketSizeSend) {
					int packetID = packetManager.getPacketID(packet.getClass());
					if (packetID != 0) {					
						PacketUtilities.writeInteger(packetID, output);
						PacketUtilities.writeInteger(packet.getPacketToClientType(), output);
						PacketUtilities.writeLong(System.currentTimeMillis(), output);
						PacketUtilities.writeInteger(packet.hasAwaitAnswerCallback() ? packet.getAwaitAnswerCallback().getID() : packet.getPendentingAnswer(), output);
						for (byte b : packetOutput.toByteArray()) {
							output.write(b);
						}
						ByteArrayOutputStream outputSize = new ByteArrayOutputStream();
						PacketUtilities.writeInteger(output.size(), outputSize);
						ByteBuffer bufSize = ByteBuffer.allocate(8);
						bufSize.put(outputSize.toByteArray());
						((Buffer) bufSize).rewind();
						//bufSize.flip();
						connection.write(bufSize, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
							@Override
							public void completed(Integer result, AsynchronousSocketChannel attachment) {
								checkNeedUpdateBytesPerSecond();
								newbytesPerSecondSent += 8;
								//System.out.println(bs + " sent " + Integer.toHexString(output.size()) + " " + output.size() + " " + result + " ");
								ByteBuffer bufPacket = ByteBuffer.allocate(output.size());
								bufPacket.put(output.toByteArray());
								((Buffer) bufPacket).flip();
								if (byteDebug) {
									log("size sent " + Integer.toHexString(output.size()) + " " + output.size() + " " + result + " ");
									log("intentando enviar el packet completo");
								}
								connection.write(bufPacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
									@Override
									public void completed(Integer result, AsynchronousSocketChannel attachment) {
										lastPacketSent = System.currentTimeMillis();
										if (byteDebug) {
											List<Byte> bs = new ArrayList<Byte>();
											for (byte b : bufPacket.array()) {
												bs.add(b);
											}
											log("Packet sent (" + bs.size() + ") - " + bs + result);
										}
										checkNeedUpdateBytesPerSecond();
										newbytesPerSecondSent += bufPacket.array().length;
										bytesSent += bufPacket.array().length;
										//System.out.println("Packet sent " + bufPacket.position() + " " + output.size() +" " + result + " ");
										//System.out.println((bufPacket.position() < output.size()) + " " + bufPacket.position() + "-" +  output.size());
										if (bufPacket.position() < output.size()) {
											writeIncompletePacket = bufPacket;
											writeIncompletePacket(packet);
										} else {
											sent(packet);
										}
									}
									@Override
									public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
										if (exc instanceof AsynchronousCloseException) {
											dropAndReconnect();
										}
										System.out.println("ERROR AL ENVIAR EL PACKET ENTERO " + packet.getClass().getName());
									}				
								});
							}
							@Override
							public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
								if (exc instanceof AsynchronousCloseException) {
									dropAndReconnect();
								}
								System.out.println("ERROR AL ENVIAR EL PACKET SIZE");
							}				
						});
					} else {
						System.err.println("El packet " + packet.getClass().getName() + " no esta registrado...");
					}
				} else {
					sendingPacket = null;
					System.err.println("No se ha enviado el packet " + packet.getClass().getSimpleName() + " porque ocupa mas de " + Utils.getBytesScaled(maxPacketSizeSend) + "(" + maxPacketSizeSend + ")" +  " (" + Utils.getBytesScaled(packetOutput.size() + 8) + " (" + (packetOutput.size() + 8) + "bytes))");
				}
			} catch (WritePacketException | WritePendingException e) {
				System.out.println("Error al escribir " + packet.getClass().getName());
				throw e;
			}		
		}
	}
	private void writeIncompletePacket(Packet packet) {
		//System.out.println("Packet incompleto leido, queda por leer " + (nextPacketSize - incompletePacket.position()) + " bytes");
		int prePosition = writeIncompletePacket.position();
		connection.write(writeIncompletePacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
			@Override
			public void completed(Integer result, AsynchronousSocketChannel attachment) {
				//System.out.println((writeIncompletePacket.position()) + " total de " + writeIncompletePacket.limit());
				int writed = writeIncompletePacket.position() - prePosition;
				checkNeedUpdateBytesPerSecond();
				newbytesPerSecondSent += writed;
				bytesSent += writed;
				if (writeIncompletePacket.position() < writeIncompletePacket.limit()) {
					writeIncompletePacket(packet);
				} else {
					sent(packet);
				}
			}
			@Override
			public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
				if (exc instanceof AsynchronousCloseException) {
					dropAndReconnect();
				}
				System.out.println("ERROR AL ESCRIBIR EL INCOMPLETO PACKET PARA ENVIAR");							
			}        	
		});
	}
	private void sent(Packet packet) {
		packetsent++;
		pendentingSendPacket.remove(packet);
		if (packet.hasSentCallback()) {
			if (byteDebug) {
				System.out.println("Ejecutando callback del packet ");
			}
			packet.getSentCallback().onSent(System.currentTimeMillis() - packet.getCurrent());
		}
		if (packet.hasAwaitAnswerCallback()) {
			awaitAnswers.put(packet.getAwaitAnswerCallback().getID(), packet.getAwaitAnswerCallback());
		}
		if (packet instanceof PacketGlobalDisconnect) {
			canReconnect = false;
		}
		if (debugMode) {
			System.out.println("Packet enviado en " + (System.currentTimeMillis() - lastTryPacketSent) + " ms");
			System.out.println("Comprobando si tiene packets pendientes que enviar...");
		}
		lastTryPacketSent = 0;
		if (!pendentingSendPacket.isEmpty()) {
			//Packet nextPacket = pendentingSendPacket.get(pendentingSendPacket.size()-1);
			Packet nextPacket = pendentingSendPacket.get(0);
			if (debugMode) {
				System.out.println("Proximo packet pendiente " + nextPacket.getClass().getName() + " para enviar");
			}
			if (nextPacket != null) {
				try {
					lastTryPacketSent = System.currentTimeMillis();
					if (byteDebug) {
						log("send by nextPacket");
					}
					sendingPacket = null;
					send(nextPacket);
					if (debugMode) {
						System.out.println("Enviando correctamente, desactivando debugmode");
						debugMode = false;
					}
				} catch (WritePacketException | WritePendingException e) {
					tryResend(nextPacket);
				}
			} else {
				System.out.println("Ha fallado el packet, se estaba intentando enviar un packet nullo? pendentingSendPacket: " + pendentingSendPacket.size());
				System.out.println("Para asegurar la conexión, se ha eliminado este packet de la lista de packets pendientes...");
				pendentingSendPacket.remove(nextPacket);
				sendingPacket = null;
			}
		} else {
			if (debugMode) {
				System.out.println("No hay packets pendientes que enviar...");
			}
			sendingPacket = null;
		}
	}
	private void readNextPacket() {
		ByteBuffer bufSize = ByteBuffer.allocate(8);
		connection.read(bufSize, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
			@Override
			public void completed(Integer result, AsynchronousSocketChannel attachment) {
				if (result == -1) {
					dropAndReconnect();
				} else {
					//System.out.println(attachment.isOpen());
					/**
	                List<Byte> bs = new ArrayList<Byte>();
	                for (byte b : bufSize.array()) {
	                	bs.add(b);
	                }
					 */
					//System.out.println(bs + " Received " + result);
					checkNeedUpdateBytesPerSecond();
					newbytesPerSecondReceived += 8; // porque al recibir el numero de bytes, el numero pesa 8 bytes
					bytesReceived += 8;
					try {
						nextPacketSize = PacketUtilities.getInteger(new ByteArrayInputStream(bufSize.array()));
						if (nextPacketSize + 8 < maxPacketSizeReceive) {
							ByteBuffer bufPacket = ByteBuffer.allocate(nextPacketSize);
							//System.out.println("illo q pasa intentando coger el packet de " + nextPacketSize);
							connection.read(bufPacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
								@Override
								public void completed(Integer result, AsynchronousSocketChannel attachment) {
									//System.out.println("test");
									//bufPacket.flip();
									checkNeedUpdateBytesPerSecond();
									newbytesPerSecondReceived += bufPacket.position();
									bytesReceived += bufPacket.position();
									//System.out.println(bs + " Received fully " + bufPacket.position() + " " + bufPacket.limit() + " " + bufPacket.capacity() + " " + nextPacketSize);
									if (bufPacket.position() < nextPacketSize) {
										readIncompletePacket = bufPacket;
										readIncompletePacket();
									} else {
										read(bufPacket);
									}
								}
								@Override
								public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
									if (exc instanceof AsynchronousCloseException) {
										dropAndReconnect();
									}
									System.out.println("ERROR AL COGER EL PACKET RECIBIDO");							
								}        	
							});
						} else {
							System.err.println("No se ha leido el siguiente packet porque ocupa mas de " + Utils.getBytesScaled(maxPacketSizeReceive) + "(" + maxPacketSizeReceive + ")" +  " (" + Utils.getBytesScaled(nextPacketSize + 8) + " (" + (nextPacketSize + 8) + "bytes))");
						}
					} catch (Exception e) {
						//e.printStackTrace();
						System.out.println("Error al leer el packet recibido");
						dropAndReconnect();
					}
				}
			}
			@Override
			public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
				dropAndReconnect();
			}        	
		});
	}
	private void readIncompletePacket() {
		//System.out.println("Packet incompleto leido, queda por leer " + (nextPacketSize - incompletePacket.position()) + " bytes");
		int prePosition = readIncompletePacket.position();
		connection.read(readIncompletePacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
			@Override
			public void completed(Integer result, AsynchronousSocketChannel attachment) {
				//System.out.println((incompletePacket.position()) + " total de " + nextPacketSize);
				int readed = readIncompletePacket.position() - prePosition;
				checkNeedUpdateBytesPerSecond();
				newbytesPerSecondReceived += readed;
				bytesReceived += readed;
				if (readIncompletePacket.position() < nextPacketSize) {
					readIncompletePacket();
				} else {
					read(readIncompletePacket);
				}
			}
			@Override
			public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
				if (exc instanceof AsynchronousCloseException) {
					dropAndReconnect();
				}
				System.out.println("ERROR AL COGER EL INCOMPLETO PACKET RECIBIDO");							
			}        	
		});
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
				if (awaitAnswer != 0 && awaitAnswers.containsKey(awaitAnswer)) {
					awaitAnswers.get(awaitAnswer).onAnswer(packet);
					awaitAnswers.remove(awaitAnswer);
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
						} else if (packet instanceof PacketFileCanSent) {
							PacketFileCanSent solicitudePacket = (PacketFileCanSent) packet;
							boolean request = canReceiveFile(solicitudePacket.getPath(), solicitudePacket.getFileType(), solicitudePacket.getBytesPerPacket(), solicitudePacket.getFileLenght());
							if (request) {
								setMaxPacketSizeReceive(solicitudePacket.getBytesPerPacket() + 50);
								filesReceiver.put(solicitudePacket.getCode(), new FileReceiver(solicitudePacket.getCode(), solicitudePacket.getBytesPerPacket(), solicitudePacket.getFileLenght(), new File("").getAbsolutePath() + "/" + solicitudePacket.getPath()));
							}
							sendPacket(new PacketFileSentRequest(solicitudePacket.getCode(), request));
						} else if (packet instanceof PacketFileSentRequest) {
							PacketFileSentRequest requestPacket = (PacketFileSentRequest) packet;
							if (requestPacket.canSent()) {
								if (filesSending.containsKey(requestPacket.getCode())) {
									FileSender fileSender = filesSending.get(requestPacket.getCode());
									setMaxPacketSizeSend(fileSender.getBytesPerPacket() + 50);
									sendPacket(new PacketFilePartOfFile(fileSender.getCode(), fileSender.getNextFileBytes()));
								} else {
									System.out.println("Se acepto el envio del archivo " + requestPacket.getCode() + " pero el FileSender no estaba creado!");
								}
							} else {
								System.out.println("No se acepto el envio del archivo " + requestPacket.getCode());
							}
						} else if (packet instanceof PacketFilePartOfFile) {
							PacketFilePartOfFile partOfFilePacket = (PacketFilePartOfFile) packet;
							if (filesReceiver.containsKey(partOfFilePacket.getCode())) {
								FileReceiver fileReceiver = filesReceiver.get(partOfFilePacket.getCode());
								if (fileReceiver.received(partOfFilePacket.getBytes())) {
									sendPacket(new PacketFilePartOfFileReceived(fileReceiver.getCode(), partOfFilePacket.getBytes().length));
									onReceibeFilePart(fileReceiver);
									if (fileReceiver.isFinished()) {
										long max = getMaxBytesReceiverOnFiles();
										if (max == -1) {
											restartMaxPacketSizeReceive();
										} else {
											setMaxPacketSizeReceive(max + 50);
										}
										System.out.println("File " + fileReceiver.getFilePath() + " downloaded succesfully in " + fileReceiver.getReceivedTime() + "ms!");
										onReceiveFile(fileReceiver.getFilePath(), fileReceiver.getFileType(), fileReceiver.getFileLenght());
									}
								} else {
									System.out.println("Se envió la cancelación del envio del archivo " + fileReceiver.getCode());
									sendPacket(new PacketFileCancelSend(fileReceiver.getCode()));
								}
							}
						} else if (packet instanceof PacketFilePartOfFileReceived) {
							PacketFilePartOfFileReceived partReceivedPacket = (PacketFilePartOfFileReceived) packet;
							if (filesSending.containsKey(partReceivedPacket.getCode())) {
								FileSender fileSender = filesSending.get(partReceivedPacket.getCode());
								long diff = fileSender.sent(partReceivedPacket.getBytesLenght());
								System.out.println("Se detecto un envio con una diferencia de " + diff + "ms de " + fileSender.getBytesPerPacket() + "(" + Utils.getBytesScaled(fileSender.getBytesPerPacket()) + ")");
								if (diff < maxTimePerPacketOnSendFiled) {
									if (!lastPacketSendFileChangedByteAmount) {
										double porcent = ((double) maxTimePerPacketOnSendFiled/diff) * 100;
										long newBytesPerPacket = (long) (fileSender.getBytesPerPacket()*porcent)/100;
										if (canChangeBytesPerPacket(fileSender.getCode(), fileSender.getFilePath(), newBytesPerPacket)) {
											System.out.println("El nuevo envio subió +" + porcent + "% " + newBytesPerPacket + "(" + Utils.getBytesScaled(newBytesPerPacket) + ")");
											sendPacket(new PacketFileCanChangeBytesPerPacket(fileSender.getCode(), newBytesPerPacket));
											lastPacketSendFileChangedByteAmount = true;
										}
									} else {
										System.out.println("el envio anteerior se cambio " + diff + "ms de " + fileSender.getBytesPerPacket() + "(" + Utils.getBytesScaled(fileSender.getBytesPerPacket()) + ")");
										lastPacketSendFileChangedByteAmount = false;
									}
								} else if (diff >= maxTimePerPacketOnSendFiled + 150) {
									double porcent = ((double) (maxTimePerPacketOnSendFiled + 150)/diff) * 100;
									long newBytesPerPacket = (long) (fileSender.getBytesPerPacket()*porcent)/100;
									if (canChangeBytesPerPacket(fileSender.getCode(), fileSender.getFilePath(), newBytesPerPacket)) {
										System.out.println("El nuevo envio bajó -" + porcent + "% " + newBytesPerPacket + "(" + Utils.getBytesScaled(newBytesPerPacket) + ")");
										fileSender.setBytesPerPacket(newBytesPerPacket);
										sendPacket(new PacketFileCanChangeBytesPerPacket(fileSender.getCode(), newBytesPerPacket));
									}
								}
								onSentFilePart(fileSender);
								if (fileSender.isFinished()) {
									long max = getMaxBytesSenderOnFiles();
									if (max == -1) {
										restartMaxPacketSizeSend();
									} else {
										setMaxPacketSizeSend(max + 50);
									}
									System.out.println("File " + fileSender.getFilePath() + " enviado completo en " + fileSender.getSentTime() + "ms!");
								} else {
									sendPacket(new PacketFilePartOfFile(fileSender.getCode(), fileSender.getNextFileBytes()));
								}
							} else {
								System.out.println("No se pudo enviar el archivo porque no existe el sender");
							}
						} else if (packet instanceof PacketFileCancelSend) {
							PacketFileCancelSend cancelPacket = (PacketFileCancelSend) packet;
							if (filesSending.containsKey(cancelPacket.getCode())) {
								System.out.println("Se ha cancelado el envio del archivo " + filesSending.get(cancelPacket.getCode()).getFilePath() + " con el codigo " + cancelPacket.getCode());
								filesSending.remove(cancelPacket.getCode());
							} else {
								System.out.println("Se ha intentando cancelar el archivo con el codigo " + cancelPacket.getCode() + " pero este sender no existia");
							}
						} else if (packet instanceof PacketFileCanChangeBytesPerPacket) {
							PacketFileCanChangeBytesPerPacket canChange = (PacketFileCanChangeBytesPerPacket) packet;
							if (filesSending.containsKey(canChange.getCode())) {
								filesSending.get(canChange.getCode()).setBytesPerPacket(canChange.getBytes());
								setMaxPacketSizeSend(canChange.getBytes() + 50);
								System.out.println("Se ha cambiado el numero de bytes por packet del sender " + canChange.getCode() + " a " + canChange.getBytes() + "(" + Utils.getBytesScaled(canChange.getBytes()) + ")");
							} else if (filesReceiver.containsKey(canChange.getCode())) {
								if (canChangeBytesPerPacket(canChange.getCode(), filesReceiver.get(canChange.getCode()).getFilePath(), canChange.getBytes())) {
									filesReceiver.get(canChange.getCode()).setBytesPerPacket(canChange.getBytes());
									setMaxPacketSizeReceive(canChange.getBytes() + 50);
									sendPacket(canChange);
									System.out.println("Se ha aceptado el cambio de bytes per packet del receiver " + canChange.getCode() + " a " + (canChange.getBytes() + 50) + "(" + Utils.getBytesScaled(canChange.getBytes() + 50) + ")");
								} else {
									System.out.println("Se ha denegado el cambio de bytes per packet del receiver " + canChange.getCode() + " a " + (canChange.getBytes() + 50) + "(" + Utils.getBytesScaled(canChange.getBytes() + 50) + ")");
								}
							} else {
								System.out.println("No se ha podido cambiar el numro de bytes por paquete en el envio de packet " + canChange.getCode() + " no se ha encontrado su sender");
							}
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
		} catch (ReadPacketException e) {
			System.err.println("Error al obtener la id del packet, posiblemente corrupto");
			e.printStackTrace();
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
	public void onSentFilePart(FileSender fileSender) {}
	public void onReceibeFilePart(FileReceiver fileReceiver) {}
	public String sendFile(String path, String dest) {
		return sendFile(path, dest, Utils.getFromSacledBytes("1MB"));
	}
	public String sendFile(String path, String dest, long bytesPerPacket) {
		FileSender sender = new FileSender(getRandomFileCode(), path, bytesPerPacket);
		filesSending.put(sender.getCode(), sender);
		sendPacket(new PacketFileCanSent(sender.getCode(), sender.getFileType(), dest, sender.getBytesPerPacket(), sender.getFileLenght()));
		return sender.getCode();
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
	public void reconnect(AsynchronousSocketChannel connection) {
		if (reconnectThread != null) {
			reconnectThread.stop();
			reconnectThread = null;
		}
		try {
			if (this.connection != null && this.connection.isOpen()) {
				this.connection.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.connection = connection;
		startRead();
		reconnecting = false;
		onReconnect();
	}
	public void dropAndReconnect() {
		if (!hasReconnectKey()) {
			disconnect();
		} else if (connected && !reconnecting) {
			if (canReconnect) {
				reconnecting = true;
				try {
					onDropCanReconnect();
					if (connection != null && connection.isOpen()) {
						connection.close();
						stopRead();
					}
					if (clientConnectionType.equals(ClientConnectionType.CLIENT_TO_SERVER)) {
						if (reconnectAttempts <= 3) {
							System.out.println("Connection drop, try reconnect in " + (reconnectAttempts*3) + "s attempt " + reconnectAttempts + "/3...");
							new Thread() {
								@Override
								public void run() {
									try {
										sleep(reconnectAttempts++*3000);
										connection = AsynchronousSocketChannel.open();
										connection.connect(new InetSocketAddress(ip, port), connection, new CompletionHandler<Void, AsynchronousSocketChannel>() {
											@Override
											public void completed(Void result, AsynchronousSocketChannel attachment) {
												lastPinged = System.currentTimeMillis();
												try {
													if (sendingPacket != null) {
														if (!sendingPacket.isIgnorable()) {
															pendentingSendPacket.add(sendingPacket);
														}
														sendingPacket = null;
													}
													send(new PacketGlobalReconnect(reconnectKey));
													System.out.println("Successfully reconnected");
													reconnectAttempts = 0;
													reconnecting = false;
													onReconnect();
												} catch (WritePendingException | WritePacketException e) {
													e.printStackTrace();
												}
												startRead();
											}
											@Override
											public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
												System.out.println("Error al conectar con el servidor " + ip + ":" + port + " , intentando denuevo...");
												reconnecting = false;
												dropAndReconnect();
											}
										});
									} catch (InterruptedException e) {
										e.printStackTrace();
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
							}.start();
						} else {
							System.out.println("Connection drop tried reconnect 3/3, connection finally dropped, disconnecting...");
							disconnect();
						}
					} else {
						System.out.println("Connection of client dropped, they can reconnect if this take more 30 seconds it will be disconnect.");
						reconnectThread = new Thread() {
							@Override
							public void run() {
								try {
									sleep(30000);
									System.out.println("Connection take more 30 seconds, disconnected.");
									disconnect();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						};
						reconnectThread.start();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				totalDisconnect();
			}
		}
	}
	public void disconnect() {
		if (!reconnecting && connection.isOpen()) {
			sendPacket(new PacketGlobalDisconnect());
		} else {
			totalDisconnect();
		}
		
	}
	private void totalDisconnect() {
		connected = false;
		reconnecting = false;
		try {
			if (connection.isOpen()) {
				connection.close();
			}
			onDisconnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (reconnectThread != null && reconnectThread.isAlive()) {
			reconnectThread.stop();
		}
		if (timeOutThread != null && timeOutThread.isAlive()) {
			timeOutThread.stop();
		}
		if (micThread != null && micThread.isAlive()) {
			micThread.stop();
			micThread = null;
		}
		for (AudioChannel channel : audioChannels.values()) {
			channel.stop();
		}
		audioChannels.clear();
	}
	public boolean isConnected() {
		return connection != null && connection.isOpen() && connectionCompleted;
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
		return answerCounter++;
	}
	private void checkTimeOutAwaitAnswers() {
		ArrayList<AwaitAnswerCallback> cloned = new ArrayList<AwaitAnswerCallback>(awaitAnswers.values());
		for (AwaitAnswerCallback aac : cloned) {
			if (aac.isTimeOut()) {
				aac.onTimeOut();
				awaitAnswers.remove(aac.getID());
			}
		}
	}
	protected void killClient() {
		timeOutThread.stop();
		stopRead();
		connection = null;
	}
}