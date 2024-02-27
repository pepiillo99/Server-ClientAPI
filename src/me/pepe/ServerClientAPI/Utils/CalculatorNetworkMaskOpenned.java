package me.pepe.ServerClientAPI.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class CalculatorNetworkMaskOpenned extends Thread {
	private int port;
	private NetworkMaskFindedCallback findCallback;
	private NetworkMaskDoneCallback doneCallback;
	private int total = 0;
	private int actual = 0;
	public CalculatorNetworkMaskOpenned(NetworkMaskFindedCallback findCallback, int port) {
		this(findCallback, port, null);
	}
	public CalculatorNetworkMaskOpenned(NetworkMaskFindedCallback findCallback, int port, NetworkMaskDoneCallback doneCallback) {
		this.doneCallback = doneCallback;
		this.findCallback = findCallback;
		this.port = port;
		start();
	}
	@Override
	public void run() {
		int[] mask = Utils.getMask();
		if (mask != null) {
			String[] ip = Utils.getLocalIP();
			int firstPos = -1;
			for (int pos = 0; pos < mask.length; pos++) {
				if (firstPos == -1) {
					if (mask[pos] == 0) {
						firstPos = pos;
					}
				}
			}
			boolean finished = false;
			while (!finished) {
				boolean counterFinished = false;
				int counter = 3;
				while (!counterFinished) {
					boolean print = true;
					if (mask[counter] > 255) {
						mask[counter] = 0;
						counter--;
						print = false;
					} else {
						counterFinished = true;
					}
					if (print) {
						String ipSol = "";
						for (int i = 0; i < 4; i++) {
							if (firstPos > i) {
								ipSol = ipSol + (ipSol.equals("") ? "" : ".") + ip[i] ;
							} else {
								ipSol = ipSol + (ipSol.equals("") ? "" : ".") + mask[i];
							}
						}
						try {
							String ipSoll = ipSol;
							AsynchronousSocketChannel connection = AsynchronousSocketChannel.open();
							connection.connect(new InetSocketAddress(ipSoll, port), connection, new CompletionHandler<Void, AsynchronousSocketChannel>() {
								@Override
								public void completed(Void result, AsynchronousSocketChannel attachment) {
									findCallback.done(ipSoll, null);
									connected();
								}
								@Override
								public void failed(Throwable exc, AsynchronousSocketChannel attachment) { 
									/* Not connection */
									connected();
								}
							});
						} catch (IOException e) {
							e.printStackTrace();
						}
						total++;
					}
					if (counterFinished) {
						mask[counter]++;
						while (true) {
							if (mask[counter] > 255) {
								mask[counter] = 0;
								counter--;
								if (counter < firstPos) {
									counterFinished = true;
									finished = true;
									break;
								}
								mask[counter] = mask[counter] + 1;
							} else {
								break;
							}
						}
					} else {
						if (counter < firstPos) {
							counterFinished = true;
							finished = true;
						}
					}
				}
			}
			System.out.println("Disponibles mascaras de red: " + total);
			try {
				sleep(5000);
				System.out.println("Conexiones posibles timeout...");
				doneCallback.done(total);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("Mascara de red no disponible, imposible buscar una posible conexion local...");
			if (doneCallback != null) {
				doneCallback.done(0);
			}
		}
	}
	private void connected() {
		actual++;
		if (actual >= total) {
			if (doneCallback != null) {
				doneCallback.done(total);
			}
		}
	}
}

