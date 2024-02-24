package me.pepe.ServerClientAPI.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class CalculatorRedMaskOpenned extends Thread {
	private int port;
	private RedMaskFindedCallback<String> callback;
	public CalculatorRedMaskOpenned(RedMaskFindedCallback<String> callback, int port) {
		this.callback = callback;
		this.port = port;
		start();
	}
	@Override
	public void run() {
		int[] mask = Utils.getMask();
		String[] ip = Utils.getLocalIP();
		int firstPos = -1;
		for (int pos = 0; pos < mask.length; pos++) {
			if (firstPos == -1) {
				if (mask[pos] == 0) {
					firstPos = pos;
				}
			}
		}
		int total = 0;
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
								callback.done(ipSoll, null);
							}
							@Override
							public void failed(Throwable exc, AsynchronousSocketChannel attachment) { /* Not connection */ }
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
	}
}

