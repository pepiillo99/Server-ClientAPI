package me.pepe.ServerClientAPI.Utils.File;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileReceiver implements AutoCloseable {
	private final String code;
	private final long fileLenght;
	private final Path root;
	private Path target;
	private Path temporary;
	private BufferedOutputStream output;
	private long received;
	private long bytesPerPacket;
	private final long startTime = System.currentTimeMillis();
	private long finishTime;
	private boolean finished;
	private boolean closed;
	private IOException error;

	public FileReceiver(String code, long bytesPerPacket, long fileLenght, String filePath) {
		this(code, bytesPerPacket, fileLenght, filePath, new File("").getAbsoluteFile());
	}

	public FileReceiver(String code, long bytesPerPacket, long fileLenght, String filePath, File directory) {
		this.code = code;
		this.bytesPerPacket = bytesPerPacket;
		this.fileLenght = fileLenght;
		this.root = directory.toPath().toAbsolutePath().normalize();
		try {
			if (bytesPerPacket <= 0 || bytesPerPacket > FileSender.MAX_BYTES_PER_PACKET || fileLenght < 0) {
				error = new IOException("Tamaño de transferencia inválido");
				return;
			}
			target = root.resolve(filePath).normalize();
			if (!isSafeTarget()) {
				error = new IOException("Destino de archivo no permitido");
				return;
			}
			Files.createDirectories(target.getParent());
			if (!isSafeTarget()) {
				error = new IOException("Destino de archivo no permitido");
				return;
			}
			temporary = Files.createTempFile(target.getParent(), ".dymos-receive-", ".part");
			output = new BufferedOutputStream(Files.newOutputStream(temporary));
			if (fileLenght == 0) {
				finish();
			}
		} catch (IOException | RuntimeException exception) {
			error = new IOException("No se pudo preparar la recepción del archivo", exception);
			close();
		}
	}

	public String getCode() {
		return code;
	}
	public synchronized long getBytesPerPacket() {
		return bytesPerPacket;
	}
	public synchronized void setBytesPerPacket(long bytesPerPacket) {
		if (bytesPerPacket > 0 && bytesPerPacket <= FileSender.MAX_BYTES_PER_PACKET) {
			this.bytesPerPacket = bytesPerPacket;
		}
	}
	public long getFileLenght() {
		return fileLenght;
	}
	public String getFilePath() {
		return target == null ? "" : target.toString();
	}
	public String getFileType() {
		String name = target == null ? "" : target.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(dot + 1);
	}
	public synchronized long getReceived() {
		return received;
	}
	public synchronized boolean received(byte[] bytes) {
		if (!isReady() || closed || finished || bytes == null || bytes.length == 0
				|| bytes.length > bytesPerPacket || bytes.length > fileLenght - received) {
			close();
			return false;
		}
		try {
			output.write(bytes);
			received += bytes.length;
			if (received == fileLenght) {
				finish();
			}
			return true;
		} catch (IOException exception) {
			error = exception;
			close();
			return false;
		}
	}
	public synchronized boolean isFinished() {
		return finished;
	}
	public synchronized long getReceivedTime() {
		return finished ? finishTime - startTime : -1;
	}
	public boolean checkFileExists() {
		return target != null && Files.exists(target);
	}
	public synchronized int getPorcentReceived() {
		return finished ? 100 : fileLenght == 0 ? 0 : (int) (100.0 * received / fileLenght);
	}
	public synchronized boolean isReady() {
		return error == null && (output != null || finished);
	}
	public synchronized IOException getError() {
		return error;
	}

	private boolean isSafeTarget() throws IOException {
		if (target == null || target.equals(root) || !target.startsWith(root) || Files.isSymbolicLink(target)) {
			return false;
		}
		Path existing = target.getParent();
		while (existing != null && !Files.exists(existing)) {
			existing = existing.getParent();
		}
		return existing != null && existing.toRealPath().startsWith(root.toRealPath());
	}

	private void finish() throws IOException {
		output.close();
		output = null;
		if (!isSafeTarget()) {
			throw new IOException("El destino del archivo ha cambiado");
		}
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
		temporary = null;
		finishTime = System.currentTimeMillis();
		finished = true;
		closed = true;
	}

	@Override
	public synchronized void close() {
		closed = true;
		try {
			if (output != null) {
				output.close();
				output = null;
			}
		} catch (IOException exception) {
			error = exception;
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
					temporary = null;
				} catch (IOException exception) {
					error = exception;
				}
			}
		}
	}
}
