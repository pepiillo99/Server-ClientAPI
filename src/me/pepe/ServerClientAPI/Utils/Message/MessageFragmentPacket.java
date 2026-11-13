package me.pepe.ServerClientAPI.Utils.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;

/** Registro explícito al final del catálogo del consumidor, para conservar IDs anteriores. */
public final class MessageFragmentPacket extends Packet {
    public static final int VERSION=1, CHUNK_BYTES=2400;
    private final String transferID, topic;
    private final int index, parts, totalBytes;
    private final byte[] checksum, payload;
    public MessageFragmentPacket() { this("","",0,0,0,new byte[0],new byte[0]); }
    public MessageFragmentPacket(String id,String topic,int index,int parts,int total,byte[] checksum,byte[] payload) {
        super(1); this.transferID=id; this.topic=topic; this.index=index; this.parts=parts; this.totalBytes=total;
        this.checksum=checksum.clone(); this.payload=payload.clone();
    }
    public String getTransferID() { return transferID; }
    public String getTopic() { return topic; }
    public int getIndex() { return index; }
    public int getParts() { return parts; }
    public int getTotalBytes() { return totalBytes; }
    public byte[] getChecksum() { return checksum.clone(); }
    public byte[] getPayload() { return payload.clone(); }
    @Override public Packet serialize(ByteArrayInputStream input) throws ReadPacketException {
        if(PacketUtilities.getInteger(input)!=VERSION) throw new ReadPacketException("Versión de fragmento no compatible");
        return new MessageFragmentPacket(PacketUtilities.getString(input),PacketUtilities.getString(input),
            PacketUtilities.getInteger(input),PacketUtilities.getInteger(input),PacketUtilities.getInteger(input),
            PacketUtilities.getBytes(input),PacketUtilities.getBytes(input));
    }
    @Override public ByteArrayOutputStream deserialize(ByteArrayOutputStream output) throws WritePacketException {
        PacketUtilities.writeInteger(VERSION,output); PacketUtilities.writeString(transferID,output);
        PacketUtilities.writeString(topic,output); PacketUtilities.writeInteger(index,output);
        PacketUtilities.writeInteger(parts,output); PacketUtilities.writeInteger(totalBytes,output);
        PacketUtilities.writeBytes(checksum,output); PacketUtilities.writeBytes(payload,output); return output;
    }
}
