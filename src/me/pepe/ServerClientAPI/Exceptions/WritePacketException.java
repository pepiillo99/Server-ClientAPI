package me.pepe.ServerClientAPI.Exceptions;

public class WritePacketException extends Exception {
	private static final long serialVersionUID = -6113774658678002980L;
	public WritePacketException(String errorMessage) {
		super(errorMessage);
	}
}
