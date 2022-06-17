package me.pepe.ServerClientAPI.Utils.File;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import me.pepe.ServerClientAPI.Utils.Utils;

public class FileSender {
	private long fileLenght = 0;
	private String filePath = "";
	private long sent = 0;
	private long bytesPerPacket = Utils.getFromSacledBytes("30KB");
	public FileSender(long fileLenght, String filePath) {
		this.fileLenght = fileLenght;
		this.filePath = filePath;
	}
	public long getFileLenght() {
		return fileLenght;
	}
	public String getFilePath() {
		return filePath;
	}
	public long getSent() {
		return sent;
	}
	public long getBytesPerPacket() {
		return bytesPerPacket;
	}
	public byte[] getNextFileBytes() {
		byte[] bytes = new byte[(int) (sent - fileLenght < bytesPerPacket ? sent - fileLenght : bytesPerPacket)];
		File file = new File(filePath);
		try {
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis);
	        bis.read(bytes,(int) sent,bytes.length);
	        bis.close();
	        return bytes;
		} catch (IOException e) {
			System.out.println("Error al encontrar el archivo que se esta enviando desde " + filePath);
		}
		return null;
	}
	public void sent(long lenght) {
		sent += lenght;
	}
}
