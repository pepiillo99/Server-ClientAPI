package me.pepe.ServerClientAPI.Utils.Message;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Utils.PacketUtilities;
import me.pepe.ServerClientAPI.Exceptions.ReadPacketException;
import me.pepe.ServerClientAPI.Exceptions.WritePacketException;

public final class MessageFragmentAckPacket extends Packet {
    private final String transferID;
    private final int index;
    private final boolean accepted;
    public MessageFragmentAckPacket() { this("",0,false); }
    public MessageFragmentAckPacket(String id,int index,boolean accepted) { super(1); transferID=id; this.index=index; this.accepted=accepted; }
    public String getTransferID() { return transferID; }
    public int getIndex() { return index; }
    public boolean isAccepted() { return accepted; }
    @Override public Packet serialize(ByteArrayInputStream input) throws ReadPacketException {
        return new MessageFragmentAckPacket(PacketUtilities.getString(input),PacketUtilities.getInteger(input),PacketUtilities.getBoolean(input));
    }
    @Override public ByteArrayOutputStream deserialize(ByteArrayOutputStream output) throws WritePacketException {
        PacketUtilities.writeString(transferID,output); PacketUtilities.writeInteger(index,output); PacketUtilities.writeBoolean(accepted,output); return output;
    }
}
