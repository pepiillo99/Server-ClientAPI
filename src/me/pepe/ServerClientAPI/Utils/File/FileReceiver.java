package me.pepe.ServerClientAPI.Utils.File;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import me.pepe.ServerClientAPI.Utils.Utils;

public class FileReceiver {
	private String code;
	private long fileLenght = 0;
	private String filePath = "";
	private long received = 0;
	private FileOutputStream fos;
	private BufferedOutputStream bos;
	private long startTime, finishTime;
	private long bytesPerPacket = 0;
	private long lastInformed = 0;
	private long lastReceived = 0;
	private long timeToInform = 0;
	public FileReceiver(String code, long bytesPerPacket, long fileLenght, String filePath) {
		this.code = code;
		this.bytesPerPacket = bytesPerPacket;
		this.fileLenght = fileLenght;
		this.filePath = filePath;
		this.startTime = System.currentTimeMillis();
		if (checkFileExists()) {
			new File(filePath).delete();
			System.out.println("Archivo eliminado!");
			try {
				fos = new FileOutputStream(filePath);
				bos = new BufferedOutputStream(fos);				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		
			//throw new IllegalArgumentException("File already exists " + filePath);
		} else {
			try {
				fos = new FileOutputStream(filePath);
				bos = new BufferedOutputStream(fos);				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
	public String getCode() {
		return code;
	}
	public long getBytesPerPacket() {
		return bytesPerPacket;
	}
	public void setBytesPerPacket(long bytesPerPacket) {
		this.bytesPerPacket = bytesPerPacket;
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
	public long getReceived() {
		return received;
	}
	public boolean received(byte[] bytes) {
	    try {
	    	if (bytesPerPacket >= bytes.length) {
				bos.write(bytes, 0, (int) bytes.length);
				received += bytes.length;
				if ((lastInformed + timeToInform) - System.currentTimeMillis() < 0) {
					lastInformed = System.currentTimeMillis();
					System.out.println(Utils.getBytesScaled(received) + "(" + received + ") recibido de " + Utils.getBytesScaled(fileLenght) + "(" + fileLenght + ") - " + getPorcentReceived() + "% " + (System.currentTimeMillis() - lastReceived) + "ms de diferencia entre paquetes");
				}
				lastReceived = System.currentTimeMillis();
				if (isFinished()) {
					bos.flush();
					fos.close();
					bos.close();
					finishTime = System.currentTimeMillis();
				}
				return true;
	    	} else {
	    		System.out.println("Se ha cancelado el envio del archivo " + code + " porque se recibio un packet de " + bytes.length + " con un maximo de " + bytesPerPacket);
	    		return false;
	    	}
	    } catch (IOException e) {
			e.printStackTrace();
		}
	    return false;
	}
	public boolean isFinished() {
		return fileLenght == received;
	}
	public long getReceivedTime() {
		if (isFinished()) {
			return finishTime - startTime;
		} else {
			return -1;
		}
	}
	public boolean checkFileExists() {
		return new File(filePath).exists();
	}
	public int getPorcentReceived() {
		return (int) (((double) received / (double) fileLenght) * 100);
	}
}