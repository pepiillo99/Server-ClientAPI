package me.pepe.ServerClientAPI.Connections;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.WritePendingException;
import java.util.ArrayList;
import java.util.List;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalDisconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalKick;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalPing;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnect;
import me.pepe.ServerClientAPI.GlobalPackets.PacketGlobalReconnectDefineKey;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;
import me.pepe.ServerClientAPI.Utils.Utils;

public abstract class ClientConnection {
	private ServerClientAPI packetManager;
	private AsynchronousSocketChannel connection;
	private ClientConnectionType clientConnectionType = null;
	private List<Packet> pendentingSendPacket = new ArrayList<Packet>();
	private int maxPacketSize = 40000;
	private int nextPacketSize;
	private long downPing = 0;
	private boolean reading = false;
	private ByteBuffer incompletePacket;
	private String ipC = "";
	public boolean debugMode = false;
	private long lastTryPacketSended = 0;
	private long lastPacketSended = 0;
	private long lastBytePerSecondUpdate = 0; // la ultima vez que actualizó los bytes por segundo
	// b/s receibed
	private int bytesPerSecondReceibed = 0;
	private int newbytesPerSecondReceibed = 0;	
	// b/s sended
	private int bytesPerSecondSended = 0;
	private int newbytesPerSecondSended = 0;
	public boolean byteDebug = false;
	private Packet sendingPacket = null;
	private boolean reconnecting = false;
	private String reconnectKey = "";
	private long timeOut = 5000;
	private long lastPinged = 0;
	private Thread timeOutThread;
	private int packetSended = 0;
	private int packetReceibed = 0;
	public ClientConnection(AsynchronousSocketChannel connection, ServerClientAPI packetManager) {
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
		reconnectKey = new BigInteger(25, Utils.random).toString(32);
		sendPacket(new PacketGlobalReconnectDefineKey(reconnectKey));
		lastPinged = System.currentTimeMillis();
		timeOutThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (isConnected() && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
						System.out.println("Client connection time out! " + reconnectKey);
						disconnect();
					}
				}
			}
		};
		timeOutThread.start();
		onConnect();
	}
	public ClientConnection(String ip, int port, ServerClientAPI packetManager) throws IOException {
		this.packetManager = packetManager;
		connection = AsynchronousSocketChannel.open();
		this.clientConnectionType = ClientConnectionType.CLIENT_TO_SERVER;
		connection.connect(new InetSocketAddress(ip, port), connection, new CompletionHandler<Void, AsynchronousSocketChannel>() {
			@Override
			public void completed(Void result, AsynchronousSocketChannel attachment) {	
				onConnect();
			}
			@Override
			public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
				System.out.println("Error al conectar con el servidor");
				onFailedConnect();
			}
		});
		lastPinged = System.currentTimeMillis();
		timeOutThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sendPacket(new PacketGlobalPing());
					if (isConnected() && (lastPinged + (timeOut * 3)) - System.currentTimeMillis() <= 0) {
						System.out.println("Server connection time out! " + reconnectKey);
						disconnect();
					}
				}
			}
		};
		timeOutThread.start();
	}
	public abstract void onConnect();
	public abstract void onFailedConnect();
	public abstract void onRecibe(Packet packet);
	public abstract void onDropCanReconnect();
	public abstract void onReconnect();
	public abstract void onDisconnect();
	public void informClient() {
		System.out.println("Hay " + this.pendentingSendPacket.size() + " packets pendientes para enviar");
		System.out.println("Ultimo packet enviado hace " + (System.currentTimeMillis() - lastPacketSended) + " ms");
		System.out.println("Se intento enviar un packet hace " + "(" + lastTryPacketSended + " - " + System.currentTimeMillis() + ")" + ((lastTryPacketSended + 2500) - System.currentTimeMillis()) + "ms IsPacketTryDown?: " + ((lastTryPacketSended + 2500) - System.currentTimeMillis() >= 0));
		System.out.println("UP: " + getBytePerSecondSended() + "bs/s DOWN: " + getBytePerSecondReceibed() + "bs/s");
		if (sendingPacket != null) {
			System.out.println("CurrentSending: " + sendingPacket.getClass() + " hace  " + (sendingPacket.getCurrent() - System.currentTimeMillis()) + "ms");
		}
	}
	public void onErrorSend() {}
	public int getBytePerSecondSended() {
		return bytesPerSecondSended;
	}
	public int getBytePerSecondReceibed() {
		return bytesPerSecondReceibed;
	}
	private void checkNeedUpdateBytesPerSecond() {
		if ((lastBytePerSecondUpdate + 1000) - System.currentTimeMillis() <= 0) {
			bytesPerSecondReceibed = newbytesPerSecondReceibed;
			newbytesPerSecondReceibed = 0;
			bytesPerSecondSended = newbytesPerSecondSended;
			newbytesPerSecondSended = 0;
			lastBytePerSecondUpdate = System.currentTimeMillis();
		} else {
			// no necesita actualizarlo
		}
	}
	public void sendPacket(Packet packet) {
		if (debugMode) {
			System.out.println("Enviando packet " + packet.getClass().getName());
		}
		if (!isConnected()) { // es probable que se este reconectando?
			if (!packet.isIgnorable()) {
				pendentingSendPacket.add(packet);
			}
		} else if (pendentingSendPacket.isEmpty()) {
			pendentingSendPacket.add(packet);
			try {
				lastTryPacketSended = System.currentTimeMillis();
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
			if (sendingPacket == null && lastTryPacketSended == 0) {
				System.out.println("Se intentará forzar el envio del packet...");
				try {
					send(packet);
				} catch (WritePacketException | WritePendingException e) {
					System.out.println("No se pudo enviar el packet " + packet.getClass().getName() + ", probablemente esté escribiendo un packet... Packets pendientes por enviar: " + pendentingSendPacket.size());
				}
				System.out.println("Packet enviado de forma forzada...");
			} else {
				if ((lastTryPacketSended + 2500) - System.currentTimeMillis() <= 0) {
					onErrorSend();
					System.out.println("Hace mas de 2 segundos y medio que se envio el ultimo packet. ¿El proceso de send esta parado? forzando el envio del packet pendiente");
					System.out.println(lastTryPacketSended + " - " + System.currentTimeMillis() + " - " + ((lastTryPacketSended + 2500) - System.currentTimeMillis())  + " - " + ((lastTryPacketSended + 2500) - System.currentTimeMillis() <= 0));
					try {
						if (byteDebug) {
							log("send by error?");
						}
						send(packet);
					} catch (WritePacketException | WritePendingException e) {
						System.out.println("No se pudo enviar el packet " + packet.getClass().getName() + ", probablemente esté escribiendo un packet... Packets pendientes por enviar: " + pendentingSendPacket.size());
					}
				} else {
					if (debugMode) {
						System.out.println("Hay " + pendentingSendPacket.size() + " packets pendientes, ya hay uno enviandose? " + sendingPacket + " " + lastTryPacketSended);
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
	public void startRead() {
		if (!reading) {
			readNextPacket();
		}
	}
	public void log(String s) {}
	private void send(Packet packet) throws WritePacketException, WritePendingException {
		if (sendingPacket == null) {
			sendingPacket = packet;
			try {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ByteArrayOutputStream packetOutput = new ByteArrayOutputStream();
				packet.deserialize(packetOutput);
				if (packetOutput.size() + 8 < maxPacketSize) {
					int packetID = packetManager.getPacketID(packet.getClass());
					if (packetID != 0) {					
						PacketUtilities.writeInteger(packetID, output);
						PacketUtilities.writeInteger(packet.getPacketToClientType(), output);
						PacketUtilities.writeLong(System.currentTimeMillis(), output);
						for (byte b : packetOutput.toByteArray()) {
							output.write(b);
						}
						ByteArrayOutputStream outputSize = new ByteArrayOutputStream();
						PacketUtilities.writeInteger(output.size(), outputSize);
						ByteBuffer bufSize = ByteBuffer.allocate(8);
						bufSize.put(outputSize.toByteArray());
						bufSize.rewind();
						//bufSize.flip();
						connection.write(bufSize, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
							@Override
							public void completed(Integer result, AsynchronousSocketChannel attachment) {
				                checkNeedUpdateBytesPerSecond();
				                newbytesPerSecondSended = newbytesPerSecondSended + 8;
								//System.out.println(bs + " sended " + Integer.toHexString(output.size()) + " " + output.size() + " " + result + " ");
								ByteBuffer bufPacket = ByteBuffer.allocate(output.size());
								bufPacket.put(output.toByteArray());
								bufPacket.flip();
				                if (byteDebug) {
					                log("size sended " + Integer.toHexString(output.size()) + " " + output.size() + " " + result + " ");
									log("intentando enviar el packet completo");
								}
								connection.write(bufPacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
									@Override
									public void completed(Integer result, AsynchronousSocketChannel attachment) {
										lastPacketSended = System.currentTimeMillis();
						                if (byteDebug) {
							                List<Byte> bs = new ArrayList<Byte>();
							                for (byte b : bufPacket.array()) {
							                	bs.add(b);
							                }
						                	log("Packet sended (" + bs.size() + ") - " + bs + result);
						                }
						                checkNeedUpdateBytesPerSecond();
						                newbytesPerSecondSended = newbytesPerSecondSended + bufPacket.array().length;
						                packetSended++;
										pendentingSendPacket.remove(packet);
										if (packet.hasSendedCallback()) {
											packet.getSendedCallback().onSend(System.currentTimeMillis() - packet.getCurrent());
										}
										if (debugMode) {
											System.out.println("Packet enviado en " + (System.currentTimeMillis() - lastTryPacketSended) + " ms");
											System.out.println("Comprobando si tiene packets pendientes que enviar...");
										}
										lastTryPacketSended = 0;
										if (!pendentingSendPacket.isEmpty()) {
											Packet nextPacket = pendentingSendPacket.get(pendentingSendPacket.size()-1);
											if (debugMode) {
												System.out.println("Proximo packet pendiente " + nextPacket.getClass().getName() + " para enviar");
											}
											if (nextPacket != null) {
												try {
													lastTryPacketSended = System.currentTimeMillis();
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
									@Override
									public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
										if (exc instanceof AsynchronousCloseException) {
											disconnect();
										}
										System.out.println("ERROR AL ENVIAR EL PACKET ENTERO");
									}				
								});
							}
							@Override
							public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
								if (exc instanceof AsynchronousCloseException) {
									disconnect();
								}
								System.out.println("ERROR AL ENVIAR EL PACKET SIZE");
							}				
						});
					} else {
						System.err.println("El packet " + packet.getClass().getName() + " no esta registrado...");
					}
				} else {
					System.err.println("No se ha enviado el packet " + packet.getClass().getSimpleName() + " porque ocupa mas de " + maxPacketSize + "bytes (" + (packetOutput.size() + 8) + "bytes)");
				}
			} catch (WritePacketException | WritePendingException e) {
				System.out.println("Error al escribir " + packet.getClass().getName());
				throw e;
			}		
		}
	}
	private void readNextPacket() {
        ByteBuffer bufSize = ByteBuffer.allocate(8);
        connection.read(bufSize, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
			@Override
			public void completed(Integer result, AsynchronousSocketChannel attachment) {
				if (result == -1) {
					disconnect();
				} else {
					//System.out.println(attachment.isOpen());
					/**
	                List<Byte> bs = new ArrayList<Byte>();
	                for (byte b : bufSize.array()) {
	                	bs.add(b);
	                }
					 */
					//System.out.println(bs + " receibed " + result);
	                checkNeedUpdateBytesPerSecond();
	                newbytesPerSecondReceibed = newbytesPerSecondReceibed + 8; // porque al recibir el numero de packet, el numero pesa 8 bytes
					try {
						nextPacketSize = PacketUtilities.getInteger(new ByteArrayInputStream(bufSize.array()));
				        ByteBuffer bufPacket = ByteBuffer.allocate(nextPacketSize);
				        //System.out.println("illo q pasa intentando coger el packet de " + nextPacketSize);
				        connection.read(bufPacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
							@Override
							public void completed(Integer result, AsynchronousSocketChannel attachment) {
								//System.out.println("test");
								//bufPacket.flip();
				                checkNeedUpdateBytesPerSecond();
				                newbytesPerSecondReceibed = newbytesPerSecondReceibed + bufPacket.position();
								//System.out.println(bs + " receibed fully " + bufPacket.position() + " " + bufPacket.limit() + " " + bufPacket.capacity() + " " + nextPacketSize);
								if (bufPacket.position() < nextPacketSize) {
									incompletePacket = bufPacket;
									readIncompletePacket();
								} else {
									read(bufPacket);
								}
							}
							@Override
							public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
								if (exc instanceof AsynchronousCloseException) {
									disconnect();
								}
								System.out.println("ERROR AL COGER EL PACKET RECIBIDO");							
							}        	
				        });
					} catch (ReadPacketException e) {
						e.printStackTrace();
						disconnect();
					}
				}
			}
			@Override
			public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
				disconnect();
			}        	
        });
	}
	private void readIncompletePacket() {
		//System.out.println("Packet incompleto leido, queda por leer " + (nextPacketSize - incompletePacket.position()) + " bytes");
		int prePosition = incompletePacket.position();
        connection.read(incompletePacket, connection, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
			@Override
			public void completed(Integer result, AsynchronousSocketChannel attachment) {
				//System.out.println((incompletePacket.position()) + " total de " + nextPacketSize);
				int readed = incompletePacket.position() - prePosition;
                checkNeedUpdateBytesPerSecond();
                newbytesPerSecondReceibed = newbytesPerSecondReceibed + readed;
				if (incompletePacket.position() < nextPacketSize) {
					readIncompletePacket();
				} else {
					read(incompletePacket);
				}
			}
			@Override
			public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
				if (exc instanceof AsynchronousCloseException) {
					disconnect();
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
				log("Packet receibed (" + testList.size() + ") " + testList);	
			}
			packetID = PacketUtilities.getInteger(input);
	        int cliendID = PacketUtilities.getInteger(input);
	        current = PacketUtilities.getLong(input);
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
                packetReceibed++;
	        	packet.setCurrent(current);
	    		downPing = System.currentTimeMillis() - packet.getCurrent();
	    		if (packet.getPacketToClientType() == 0) {
	    			if (packet instanceof PacketGlobalReconnectDefineKey) {
	    				PacketGlobalReconnectDefineKey defineKey = (PacketGlobalReconnectDefineKey) packet;
	    				reconnectKey = defineKey.getKey();
	    			} else if (packet instanceof PacketGlobalReconnect) {
	    				PacketGlobalReconnect reconnectPacket = (PacketGlobalReconnect) packet;
	    				if (reconnectPacket.getKey().equals(reconnectKey)) {
	    					log("La key reconnect es correcta, reconectando conexión del cliente...");
	    					//reconnect();
	    				} else {
	    					log("La key reconnect ha fallado... Desconectando cliente...");
	    					disconnect();
	    				}
	    			} else if (packet instanceof PacketGlobalDisconnect) {
	    				disconnect();
	    			} else if (packet instanceof PacketGlobalKick) {
	    				if (clientConnectionType == ClientConnectionType.SERVER_TO_CLIENT) {
	    					disconnect();
	    				}
	    			} else if (packet instanceof PacketGlobalPing) {
	    				lastPinged = System.currentTimeMillis();
	    				if (clientConnectionType == ClientConnectionType.SERVER_TO_CLIENT) {
	    					sendPacket(packet);
	    				} else {
	    					//System.out.println("Ping: " + (System.currentTimeMillis() - packet.getCurrent()) + "ms up-" + bytesPerSecondSended + " down-" + bytesPerSecondReceibed);
	    				}
	    			}
	    		} else {
	    			onRecibe(packet);
	    		}
	        } else {
				System.err.println("Packet recibido con ID " + packetID + " no registrado...");
	        }
		} catch (ReadPacketException e) {
			System.err.println("Error al obtener la id del packet, posiblemente corrupto");
			e.printStackTrace();
		} finally {
			nextPacketSize = 0;
			readNextPacket();
		}
	}
	public void reconnect(AsynchronousSocketChannel connection) {
		try {
			if (connection != null && connection.isOpen()) {
				connection.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.connection = connection;
	}
	public void dropAndReconnect() {
		try {
			if (connection.isOpen()) {
				connection.close();
				onDropCanReconnect();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void disconnect() {
		try {
			if (connection.isOpen()) {
				connection.close();
				onDisconnect();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (timeOutThread != null && timeOutThread.isAlive()) {
			timeOutThread.stop();
		}
	}
	public boolean isConnected() {
		return connection != null && connection.isOpen();
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
	public int getPacketSended() {
		return packetSended;
	}
	public int getPacketReceibed() {
		return packetReceibed;
	}
}