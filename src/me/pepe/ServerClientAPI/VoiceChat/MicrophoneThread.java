package me.pepe.ServerClientAPI.VoiceChat;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import me.pepe.ServerClientAPI.GlobalPackets.VoiceChat.PacketVoiceChatSend;
import me.pepe.ServerClientAPI.Utils.SoundData;
import me.pepe.ServerClientAPI.Utils.Utils;

public abstract class MicrophoneThread extends Thread {
	private double amplification = 1.0;
	private TargetDataLine mic;
	private AudioFormat audioFormat = new AudioFormat(11025f, 8, 1, true, true); //11.025khz, 8bit, mono, signed, big endian (changes nothing in 8 bit) ~8kb/s
	private PacketVoiceChatSend lastData;
	private boolean muted = false;
	private int noise = 0;
	private float db;
	private float rms;
	private int minNoise = 0;
	private int maxInfoPerPacket = 1024;
	public MicrophoneThread() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, null);
        mic = (TargetDataLine) (AudioSystem.getLine(info));
        mic.open(audioFormat);
        mic.start();
	}
	public PacketVoiceChatSend getLastData() { // que sea el ultimo dato no quiere decir que se haya enviado...
		return lastData;
	}
	public int getTotal() {
		if (lastData != null) {
			byte[] data = lastData.getData();
			int total = 0;
            for(int i = 0; i < data.length; i++) {
            	total += amplification*Math.abs(data[i]);
            }
            total*=2.5;
            total/=data.length;
            return total;
		}
		return 0;
	}
	public boolean isMuted() {
		return muted;
	}
	public void setMuted(boolean muted) {
		this.muted = muted;
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
			if (mic.available() >= maxInfoPerPacket) {
				byte[] data = new byte[maxInfoPerPacket];
				while (mic.available() >= maxInfoPerPacket) {
					mic.read(data, 0, data.length);
				}
                int noise = 0;
                for (int i = 0; i < data.length; i++) {
                    data[i] *= amplification;
                    noise += Math.abs(data[i]);
                }
                noise *= 2.5;
                noise /= data.length;
                this.noise = noise;
                SoundData soundData = Utils.processSound(data);
                db = soundData.getDB();
                rms = soundData.getRMS();
                System.out.println("Microfono=  {ruido: " + noise + ", db: " + db + ", rms: " + rms + "}");
                Utils.processSound(data);
                if (noise <= minNoise) {
                	//send(new PacketVoiceChatSend(new byte[maxInfoPerPacket]));
                } else {
                	try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        GZIPOutputStream go = new GZIPOutputStream(baos);
                        go.write(data);
                        go.flush();
                        go.close();
                        baos.flush();
                        baos.close();
                        System.out.println("Enviando: " + baos.toByteArray().length);
                        send(new PacketVoiceChatSend(baos.toByteArray()));
                	} catch (Exception ex) {
                		ex.printStackTrace();
                	}
                }
			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	public abstract void send(PacketVoiceChatSend packet);
}
