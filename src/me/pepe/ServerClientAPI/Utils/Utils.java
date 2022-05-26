package me.pepe.ServerClientAPI.Utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;

public class Utils {
	public static Random random = new Random();
	public static String[] getLocalIP() {
		try {
			String local = InetAddress.getLocalHost().getHostAddress();
			return local.split("\\.");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}
	public static String getMaskString() {
		int[] mask = getMask();
		return mask[0] + "." + mask[1] + "." + mask[2] + "." + mask[3];
	}
	public static int[] getMask() {
		try{
			InetAddress localHost = Inet4Address.getLocalHost();
			NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localHost);
			int shft = 0xffffffff<<(32-networkInterface.getInterfaceAddresses().get(0).getNetworkPrefixLength());
			int oct1 = ((byte) ((shft&0xff000000)>>24)) & 0xff;
			int oct2 = ((byte) ((shft&0x00ff0000)>>16)) & 0xff;
			int oct3 = ((byte) ((shft&0x0000ff00)>>8)) & 0xff;
			int oct4 = ((byte) (shft&0x000000ff)) & 0xff;
			return new int[] {oct1, oct2, oct3, oct4};
		}catch(UnknownHostException | SocketException  e){
			System.out.println("Error: "+e);
		}
		return null;
	}
}
