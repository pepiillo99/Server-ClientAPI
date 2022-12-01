package me.pepe.ServerClientAPI.Utils;

import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Random;

public class Utils {
	public static Random random = new Random();
	public static DecimalFormat decimalFormat = new DecimalFormat("#,###.##");
	public static String getLocalIPString() {
		String[] ip = getLocalIP();
		return ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3];
	}
	public static String[] getLocalIP() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual() || iface.getDisplayName().equals("VirtualBox Host-Only Ethernet Adapter")) {
                	continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                    	System.out.println(addr.getHostAddress() +  " - " + iface.getDisplayName());
                        ip = addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return ip.split("\\.");
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
	public static String getBytesScaled(long bytes) {
		if (bytes / 1000000000000L != 0) {
			return decimalFormat.format(divideWithDecimals(bytes, 1000000000000L)) + "TB";
		}
		if (bytes / 1000000000 != 0) {
			return decimalFormat.format(divideWithDecimals(bytes, 1000000000L)) + "GB";
		}
		if (bytes / 1000000 != 0) {
			return decimalFormat.format(divideWithDecimals(bytes, 1000000L)) + "MB";
		}
		if (bytes / 1000 != 0) {
			return decimalFormat.format(divideWithDecimals(bytes, 1000L)) + "KB";
		}
		return bytes + "B";
	}
	public static long getFromSacledBytes(String scaled) { // si viene de getBytesScaled es probable que haya perdidas de información porque este redondea...
		if (scaled.endsWith("TB") || scaled.endsWith("GB") || scaled.endsWith("MB") || scaled.endsWith("KB")) {
			try {
				long bytes = Long.valueOf(scaled.substring(0,  scaled.length() - 2));
				if (scaled.endsWith("TB")) {
					bytes *= 1000000000000L;
				} else if (scaled.endsWith("GB")) {
					bytes *= 1000000000;
				} else if (scaled.endsWith("MB")) {
					bytes *= 1000000;
				} else if (scaled.endsWith("KB")) {
					bytes *= 1000;
				}
				return bytes;
			} catch (NumberFormatException ex) {}
		} else if (scaled.endsWith("B")) {
			try {
				return Long.valueOf(scaled.substring(0,  scaled.length() - 1));
			} catch (NumberFormatException ex) {}
		}
		return -1;
	}
	private static BigDecimal divideWithDecimals(long a, long b) {
		BigDecimal bdec = new BigDecimal(a);
		BigDecimal bdecRes = bdec.divide(new BigDecimal(b));
		return bdecRes;
	}
	public static SoundData processSound(byte[] buf) {
		float[] samples = new float[buf.length / 2];
		int b = buf.length;
		// convert bytes to samples here
		for(int i = 0, s = 0; i < b;) {
			int sample = 0;
			sample |= buf[i++] & 0xFF; // (reverse these two lines
			sample |= buf[i++] << 8;   //  if the format is big endian)
			// normalize to range of +/-1.0f
			samples[s++] = sample / 32768f;
		}
		float rms = 0f;
		float peak = 0f;
		for(float sample : samples) {
			float abs = Math.abs(sample);
			if(abs > peak) {
				peak = abs;
			}
			rms += sample * sample;
		}
		rms = (float)Math.sqrt(rms / samples.length);
		return new SoundData(peak, calculateRMSLevel(buf));
	}
	public static int calculateRMSLevel(byte[] audioData) {
	    // audioData might be buffered data read from a data line
	    long lSum = 0;
	    for (int i = 0; i < audioData.length; i++) {
	        lSum = lSum + audioData[i];
	    }

	    double dAvg = lSum / audioData.length;

	    double sumMeanSquare = 0d;
	    for (int j = 0; j < audioData.length; j++) {
	        sumMeanSquare = sumMeanSquare + Math.pow(audioData[j] - dAvg, 2d);
	    }

	    double averageMeanSquare = sumMeanSquare / audioData.length;
	    return (int) (Math.pow(averageMeanSquare, 0.5d) + 0.5);
	}
}
