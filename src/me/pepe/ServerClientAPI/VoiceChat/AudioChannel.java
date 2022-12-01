package me.pepe.ServerClientAPI.VoiceChat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChatReceive;
import me.pepe.ServerClientAPI.Utils.SoundData;
import me.pepe.ServerClientAPI.Utils.Utils;

public class AudioChannel extends Thread {
	private int channelID;
	private double amplification = 1.0;
	private List<PacketVoiceChatReceive> queue = new ArrayList<PacketVoiceChatReceive>();
	private AudioFormat audioFormat = new AudioFormat(11025f, 8, 1, true, true); //11.025khz, 8bit, mono, signed, big endian (changes nothing in 8 bit) ~8kb/s
	private SourceDataLine speaker;
	private boolean earning = true;
	private float db;
	private float rms;
	private int noise;
	private int maxInfoPerPacket = 1024;
	public AudioChannel(int channelID) {
		this.channelID = channelID;
	}
	public int getChannelID() {
		return channelID;
	}
	public List<PacketVoiceChatReceive> getQueue() {
		return queue;
	}
	public AudioFormat getAudioFormat() {
		return audioFormat;
	}
	public boolean isEarning() {
		return earning;
	}
	public void setEarning(boolean earning) {
		this.earning = earning;
	}
	public void addToQueue(PacketVoiceChatReceive packet) {
		if (earning) {
			queue.add(packet);
		}
	}
	public int getNoise() {
		return noise;
	}
	public float getDB() {
		return db;
	}
	public float getRMS() {
		return rms;
	}
	@Override
	public void run() {
		while (true) {
			try {
				DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
				speaker = (SourceDataLine) AudioSystem.getLine(info);
				speaker.open(audioFormat);
				speaker.start();
				if (queue.isEmpty()) {
					sleep(10);
				} else {
					PacketVoiceChatReceive packet = queue.get(0);
					queue.remove(0);
					if (packet.getData().length == 0) { // silencio incomodo xd
						byte[] noise = new byte[maxInfoPerPacket];
						for (int i = 0; i < noise.length; i++) {
							noise[i] = (byte) ((Math.random() * 3) - 1);
						}
						speaker.write(noise, 0, noise.length);
					} else {
						GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(packet.getData()));
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						int b = 0;
						while ((b = gis.read()) != -1) {
							baos.write((byte) b);
						}
						byte[] toPlay = baos.toByteArray();
		                int noise = 0;
		                for (int i = 0; i < toPlay.length; i++) {
		                    toPlay[i] *= amplification;
		                    noise += Math.abs(toPlay[i]);
		                }
		                noise *= 2.5;
		                noise /= toPlay.length;
		                this.noise = noise;
		                SoundData soundData = Utils.processSound(toPlay);
		                db = soundData.getDB();
		                rms = soundData.getRMS();
		                System.out.println("Audio=  {ruido: " + noise + ", db: " + db + ", rms: " + rms + "}");
						speaker.write(toPlay, 0, toPlay.length);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				if (speaker != null) {
					speaker.close();
				}
			}
		}
	}
}
