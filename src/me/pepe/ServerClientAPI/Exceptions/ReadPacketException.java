package me.pepe.ServerClientAPI.Exceptions;

public class ReadPacketException extends Exception {
	private static final long serialVersionUID = -8966868690252761395L;
	public ReadPacketException(String errorMessage) {
		super(errorMessage, new Throwable(errorMessage));
	}
}
