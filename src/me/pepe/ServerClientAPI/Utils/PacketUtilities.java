package me.pepe.ServerClientAPI.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;

public class PacketUtilities {
	private static String getString(int size, ByteArrayInputStream byteArray) { // el size es cuanto mide, si falla puede causar errores (size * 255)
		int stringLeght = 0;
		for (int i = size; i > 0; i--) {
			stringLeght += byteArray.read();
		}
        byte[] stringArray = new byte[stringLeght];
        byteArray.read(stringArray, 0, stringLeght);
        return new String(stringArray, StandardCharsets.UTF_8);
	}	
	private static String getSizedString(int min, int max, ByteArrayInputStream byteArray) throws ReadPacketException { // la linea debe de ser menos de 255
		if (max > 255) {
			throw new ReadPacketException("El maximo no puede superar los 255");
		} else if (min < 0) {
			throw new ReadPacketException("El minimo debe de ser inferior a 0");
		} else {
			String string = getString(1, byteArray);
			if (string.length() > max) {
				throw new ReadPacketException("La linea supera el maximo (" + string.length() + "/" + max + ") '" + string + "'");
			} else if (string.length() < min) {
				throw new ReadPacketException("La linea es inferior al minimo (" + min + "/" + string.length() + ") '" + string + "'");
			} else {
				return string;
			}
		}
	}
	public static String getString(ByteArrayInputStream byteArray) throws ReadPacketException {
		int stringLeght = getInteger(byteArray);
        byte[] stringArray = new byte[stringLeght];
        byteArray.read(stringArray, 0, stringLeght);
        return new String(stringArray, StandardCharsets.UTF_8);
	}
	public static boolean getBoolean(ByteArrayInputStream byteArray) throws ReadPacketException {
		byte b = (byte) byteArray.read();
		if (b < 0) {
			throw new ReadPacketException("No se ha podido obtener el boolean adecuadamente (" + b + ")");
		} else {
			return b == 1 ? true : false;
		}
	}
	public static int getInteger(ByteArrayInputStream byteArray) throws ReadPacketException {
		String hex = getSizedString(1, 8, byteArray);
		return Integer.parseUnsignedInt(hex, 16);
	}
	public static double getDouble(ByteArrayInputStream byteArray) throws ReadPacketException {
		String hex = getSizedString(7, 23, byteArray);
		return Double.valueOf(hex);
	}
	public static long getLong(ByteArrayInputStream byteArray) throws ReadPacketException {
		String hex = getSizedString(1, 16, byteArray);
		return Long.parseUnsignedLong(hex, 16);
	}
	public static UUID getUUID(ByteArrayInputStream byteArray) {
		String uuid = getString(1, byteArray);
		return UUID.fromString(uuid);
	}
	public static List<String> getListString(ByteArrayInputStream byteArray) throws ReadPacketException {
		List<String> list = new ArrayList<String>();
		int listSize = getInteger(byteArray);
		for (int i = listSize; i > 0; i--) {
			list.add(getString(byteArray));
		}
		return list;
	}
	public static byte[] getBytes(ByteArrayInputStream byteArray) throws ReadPacketException {
		int size = getInteger(byteArray);
        byte[] bytes = new byte[size];
        byteArray.read(bytes, 0, size);
        return bytes;
	}
	public static List<byte[]> getListBytes(ByteArrayInputStream byteArray) throws ReadPacketException {
		List<byte[]> list = new ArrayList<byte[]>();
		int listSize = getInteger(byteArray);
		for (int i = listSize; i > 0; i--) {
			list.add(getBytes(byteArray));
		}
		return list;
	}
	private static void writeString(String s, int size, ByteArrayOutputStream toInfo) throws WritePacketException { // el size es cuanto va a medir (size * 255)
		byte[] stringArray = s.getBytes(StandardCharsets.UTF_8);
		int stringLeght = stringArray.length;
		if (stringArray.length < size * 255) {
			for (int i = size; i > 0; i--) {
				if (stringLeght >= 255) {
					toInfo.write(255);
					stringLeght -= 255;
				} else {
					toInfo.write(stringLeght);
					stringLeght -= stringLeght;
				}
			}
			write(stringArray, toInfo);
		} else {
			throw new WritePacketException("Error al escribir linea contada, la linea con tiene mas de " + (size * 255) + " letras (" + size + ") size");
		}
	}
	public static void writeString(String s, ByteArrayOutputStream toInfo) throws WritePacketException {
		byte[] stringArray = s.getBytes(StandardCharsets.UTF_8);
		int stringLeght = stringArray.length;
		writeInteger(stringLeght, toInfo);
		write(stringArray, toInfo);
	}
	public static void writeBoolean(boolean b, ByteArrayOutputStream toInfo) {
		toInfo.write(b ? 1 : 0);
	}
	public static void writeInteger(int i, ByteArrayOutputStream toInfo) throws WritePacketException {
		String hex = Integer.toHexString(i);
		if (hex.length() < 1 || hex.length() > 8) { 
			throw new WritePacketException("Error al escribir el integer " + i + "- " + hex);
		} else {
			writeString(hex, 1, toInfo);
		}
	}
	public static void writeDouble(double d, ByteArrayOutputStream toInfo) throws WritePacketException {
		String hex = Double.toHexString(d);
		if (hex.length() < 7 || hex.length() > 23) { 
			throw new WritePacketException("Error al escribir el double " + d + "- " + hex);
		}
		writeString(hex, 1, toInfo);
	}
	public static void writeLong(long l, ByteArrayOutputStream toInfo) throws WritePacketException {
		String hex = Long.toHexString(l);
		if (hex.length() < 1 || hex.length() > 16) { 
			throw new WritePacketException("Error al escribir el long " + l + "- " + hex);
		}
		writeString(hex, 1, toInfo);
	}
	public static void writeUUID(UUID uuid, ByteArrayOutputStream toInfo) throws WritePacketException {
		writeString(uuid.toString(), 1, toInfo);
	}
	public static void writeListString(List<String> list, ByteArrayOutputStream toInfo) throws WritePacketException {
		writeInteger(list.size(), toInfo);
		for (String s : list) {
			writeString(s, toInfo);
		}
	}
	public static void writeBytes(byte[] bytes, ByteArrayOutputStream toInfo) throws WritePacketException {
		writeInteger(bytes.length, toInfo);
		write(bytes, toInfo);
	}
	public static void writeListBytes(List<byte[]> listBytes, ByteArrayOutputStream toInfo) throws WritePacketException {
		writeInteger(listBytes.size(), toInfo);
		for (byte[] b : listBytes) {
			writeBytes(b, toInfo);
		}		
	}
	private static void write(byte[] bytes, ByteArrayOutputStream toInfo) {
		try {
			toInfo.write(bytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
