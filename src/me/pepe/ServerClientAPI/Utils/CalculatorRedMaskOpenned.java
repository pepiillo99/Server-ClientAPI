package me.pepe.ServerClientAPI.Utils;

import java.io.IOException;

import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.ServerClientAPI;
import me.pepe.ServerClientAPI.Connections.ClientConnection;

public class CalculatorRedMaskOpenned extends Thread {
	private ServerClientAPI svAPI;
	private int port;
	private RedMaskFindedCallback<String> callback;
	public CalculatorRedMaskOpenned(ServerClientAPI svAPI, RedMaskFindedCallback<String> callback, int port) {
		this.svAPI = svAPI;
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
						ClientConnection conn = new ClientConnection(ipSol, port, svAPI) {
							@Override
							public void onConnect() {
								callback.done(ipSoll, null);
								disconnect();
							}
							@Override
							public void onFailedConnect() {}
							@Override
							public void onRecibe(Packet packet) {}
							@Override
							public void onDropCanReconnect() {}
							@Override
							public void onReconnect() {}
							@Override
							public void onDisconnect() {}						
						};
						conn.setTimeOutTime(2000);
					} catch (IOException e) {
						e.printStackTrace();
					}
					total++;
				}
				if (counterFinished) {
					mask[counter]++;
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

