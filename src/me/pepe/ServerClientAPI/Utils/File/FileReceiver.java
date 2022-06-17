package me.pepe.ServerClientAPI.Utils.File;

public class FileReceiver {
	private long fileLenght = 0;
	private String filePath = "";
	private long received = 0;
	public FileReceiver(long fileLenght, String filePath) {
		this.fileLenght = fileLenght;
		this.filePath = filePath;
	}
	public long getFileLenght() {
		return fileLenght;
	}
	public String getFilePath() {
		return filePath;
	}
	public long getReceived() {
		return received;
	}
	public void received(long lenght) {
		received += lenght;
	}
}
