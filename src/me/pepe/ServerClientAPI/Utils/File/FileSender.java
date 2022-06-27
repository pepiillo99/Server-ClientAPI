package me.pepe.ServerClientAPI.Utils.File;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileSender {
	private String code;
	private long fileLenght = 0;
	private String filePath = "";
	private long sent = 0;
	private long bytesPerPacket= -1;
	private long startTime, finishTime;
	private long lastInfo = 0;
	/*
	 * default sent 1MB per packet
	 */
	public FileSender(String code, String filePath, long bytesPerPacket) {
		this(code, new File(filePath), bytesPerPacket);
	}
	public FileSender(String code, File file, long bytesPerPacket) {
		this.code = code;
		this.startTime = System.currentTimeMillis();
		this.bytesPerPacket = bytesPerPacket;
		if (file != null && file.exists()) {
			this.fileLenght = file.length();
			this.filePath = file.getPath();
		} else {
			if (file != null) {
				throw new NullPointerException("El archivo " + file.getPath() + " no existe!");
			} else {
				throw new NullPointerException("El archivo que intenta enviar no existe!");
			}
		}
	}
	public String getCode() {
		return code;
	}
	public long getFileLenght() {
		return fileLenght;
	}
	public String getFilePath() {
		return filePath;
	}
	public String getFileType() {
		String[] split = filePath.split("\\.");
	       return split[split.length-1];
	}
	public long getSent() {
		return sent;
	}
	public long getBytesPerPacket() {
		return bytesPerPacket;
	}
	public void setBytesPerPacket(long bytesPerPacket) {
		this.bytesPerPacket = bytesPerPacket;
	}
	public byte[] getNextFileBytes() {
		byte[] bytes = new byte[(int) (fileLenght - sent < bytesPerPacket ? fileLenght - sent : bytesPerPacket)];
		File file = new File(filePath);
		try {
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			//System.out.println(bytes.length + " - " + sent + " - " + (sent + bytes.length) + " - " + fileLenght);
			bis.skip(sent);
	        bis.read(bytes,0,bytes.length);
	        bis.close();
	        return bytes;
		} catch (IOException e) {
			System.out.println("Error al encontrar el archivo que se esta enviando desde " + filePath);
		}
		return null;
	}
	public void sent(long lenght) {
		sent += lenght;
		if ((lastInfo + 5000) - System.currentTimeMillis() < 0) {
			lastInfo = System.currentTimeMillis();
			System.out.println(sent + " enviado de " + fileLenght + " - " + getPorcentSent() + "% - " + code);
		}
		if (isFinished()) {
			finishTime = System.currentTimeMillis();
		}
	}
	public boolean isFinished() {
		return sent == fileLenght;
	}
	public long getSentTime() {
		if (isFinished()) {
			return finishTime - startTime;
		} else {
			return -1;
		}
	}
	public int getPorcentSent() {
		return (int) (((double) sent / (double) fileLenght) * 100);
	}
}