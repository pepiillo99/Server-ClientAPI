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
	private static String getString(int size, ByteArrayInputStream byteArray) throws ReadPacketException {
		int stringLeght = 0;
		for (int i = size; i > 0; i--) {
			int length = byteArray.read();
			if (length < 0) {
				throw new ReadPacketException("Longitud de texto incompleta");
			}
			stringLeght += length;
		}
		byte[] stringArray = readBytes(stringLeght, byteArray);
        return new String(stringArray, StandardCharsets.UTF_8);
	}	
	private static String getSizedString(int min, int max, ByteArrayInputStream byteArray) throws ReadPacketException { // la linea debe de ser menos de 255
		if (max > 255) {
			throw new ReadPacketException("El maximo no puede superar los 255");
		} else if (min < 0) {
			throw new ReadPacketException("El mínimo debe ser igual o superior a 0");
		} else {
			String string = getString(1, byteArray);
			if (string.length() > max) {
				throw new ReadPacketException("La longitud del texto supera el máximo");
			} else if (string.length() < min) {
				throw new ReadPacketException("La longitud del texto es inferior al mínimo");
			} else {
				return string;
			}
		}
	}
	public static String getString(ByteArrayInputStream byteArray) throws ReadPacketException {
		byte[] stringArray = readBytes(getInteger(byteArray), byteArray);
        return new String(stringArray, StandardCharsets.UTF_8);
	}
	public static boolean getBoolean(ByteArrayInputStream byteArray) throws ReadPacketException {
		int b = byteArray.read();
		if (b != 0 && b != 1) {
			throw new ReadPacketException("No se ha podido obtener el boolean adecuadamente (" + b + ")");
		} else {
			return b == 1 ? true : false;
		}
	}
	public static int getInteger(ByteArrayInputStream byteArray) throws ReadPacketException {
		String hex = getSizedString(1, 8, byteArray);
		try {
			return Integer.parseUnsignedInt(hex, 16);
		} catch (NumberFormatException exception) {
			throw new ReadPacketException("Entero inválido");
		}
	}
	public static double getDouble(ByteArrayInputStream byteArray) throws ReadPacketException {
		String hex = getSizedString(7, 23, byteArray);
		try {
			return Double.valueOf(hex);
		} catch (NumberFormatException exception) {
			throw new ReadPacketException("Decimal inválido");
		}
	}
	public static long getLong(ByteArrayInputStream byteArray) throws ReadPacketException {
		String hex = getSizedString(1, 16, byteArray);
		try {
			return Long.parseUnsignedLong(hex, 16);
		} catch (NumberFormatException exception) {
			throw new ReadPacketException("Entero largo inválido");
		}
	}
	public static UUID getUUID(ByteArrayInputStream byteArray) {
		try {
			return UUID.fromString(getString(1, byteArray));
		} catch (ReadPacketException exception) {
			throw new IllegalArgumentException("UUID incompleto", exception);
		}
	}
	public static List<String> getListString(ByteArrayInputStream byteArray) throws ReadPacketException {
		List<String> list = new ArrayList<String>();
		int listSize = getInteger(byteArray);
		validateLength(listSize, byteArray.available());
		for (int i = listSize; i > 0; i--) {
			list.add(getString(byteArray));
		}
		return list;
	}
	public static byte[] getBytes(ByteArrayInputStream byteArray) throws ReadPacketException {
		return readBytes(getInteger(byteArray), byteArray);
	}
	public static List<byte[]> getListBytes(ByteArrayInputStream byteArray) throws ReadPacketException {
		List<byte[]> list = new ArrayList<byte[]>();
		int listSize = getInteger(byteArray);
		validateLength(listSize, byteArray.available());
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

	private static void validateLength(int length, int available) throws ReadPacketException {
		if (length < 0 || length > available) {
			throw new ReadPacketException("La longitud declarada supera los datos disponibles");
		}
	}

	private static byte[] readBytes(int length, ByteArrayInputStream input) throws ReadPacketException {
		validateLength(length, input.available());
		byte[] bytes = new byte[length];
		if (length > 0 && input.read(bytes, 0, length) != length) {
			throw new ReadPacketException("Datos incompletos");
		}
		return bytes;
	}
}
