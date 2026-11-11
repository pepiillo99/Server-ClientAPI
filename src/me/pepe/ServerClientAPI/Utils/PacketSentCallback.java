package me.pepe.ServerClientAPI.Utils;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PacketSentCallback {
	private final AtomicBoolean completed = new AtomicBoolean();
	public abstract void onSent(long miliseconds);

	public void onError(Exception exception) {}

	public void complete(long milliseconds) {
		if (completed.compareAndSet(false, true)) {
			onSent(milliseconds);
		}
	}

	public void fail(Exception exception) {
		if (completed.compareAndSet(false, true)) {
			onError(exception);
		}
	}
}
