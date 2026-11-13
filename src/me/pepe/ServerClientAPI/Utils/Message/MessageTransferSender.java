package me.pepe.ServerClientAPI.Utils.Message;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Connections.ClientConnection;
import me.pepe.ServerClientAPI.Utils.PacketFactory;
import me.pepe.ServerClientAPI.Utils.RequestHandle;
import me.pepe.ServerClientAPI.Utils.RequestOptions;
import me.pepe.ServerClientAPI.Utils.ResponseCallback;

/** Fragmentación genérica en memoria, con límites y confirmación de aplicación al finalizar. */
public final class MessageTransferSender implements AutoCloseable {
    public interface Callback { void onProgress(int bytes,int total); void done(Exception failure); }
    private final ClientConnection connection;
    private final int maxBytes, maxMessages;
    private final long maxBufferedBytes;
    private final Queue<Transfer> queue=new ArrayDeque<Transfer>();
    private Transfer current;
    private boolean closed;
    private long reservedBytes;
    public MessageTransferSender(ClientConnection connection,int maxBytes) {
        this(connection,maxBytes,8,maxBytes*4L);
    }
    public MessageTransferSender(ClientConnection connection,int maxBytes,int maxMessages,long maxBufferedBytes) {
        if(connection==null || maxBytes<1 || maxMessages<1 || maxBufferedBytes<maxBytes)
            throw new IllegalArgumentException("Límites de mensajes no válidos");
        this.connection=connection; this.maxBytes=maxBytes; this.maxMessages=maxMessages; this.maxBufferedBytes=maxBufferedBytes;
    }
    public RequestHandle send(String topic,byte[] bytes,RequestOptions policy,Callback callback) {
        if(callback==null) throw new IllegalArgumentException("Falta el callback");
        Transfer transfer;
        try {
            if(topic==null || topic.isEmpty() || topic.length()>128 || bytes==null || bytes.length>maxBytes || policy==null) {
                connection.dispatchCallback(new Runnable() { @Override public void run() { callback.done(new IOException("Mensaje no válido o demasiado grande")); } });
                return new RequestHandle() { @Override public boolean cancel() { return false; } @Override public boolean isDone() { return true; } };
            }
            transfer=new Transfer(topic,bytes.clone(),policy,callback);
        } catch(Exception failure) {
            connection.dispatchCallback(new Runnable() { @Override public void run() { callback.done(failure); } });
            return new RequestHandle() { @Override public boolean cancel() { return false; } @Override public boolean isDone() { return true; } };
        }
        synchronized(this) {
            if(closed || queue.size()+(current==null?0:1)>=maxMessages || reservedBytes+(long)bytes.length>maxBufferedBytes) {
                complete(transfer,new IOException("La cola de mensajes está llena o cerrada")); return transfer;
            }
            transfer.reserved=true; reservedBytes+=bytes.length; queue.add(transfer);
        }
        next(); return transfer;
    }
    private void next() {
        Transfer transfer;
        synchronized(this) {
            if(closed || current!=null || queue.isEmpty()) return;
            current=queue.remove(); transfer=current;
        }
        sendPart(transfer);
    }
    private void sendPart(final Transfer transfer) {
        if(transfer.done.get()) return;
        final int index=transfer.index;
        final int offset=index*MessageFragmentPacket.CHUNK_BYTES;
        final byte[] part=Arrays.copyOfRange(transfer.bytes,offset,Math.min(offset+MessageFragmentPacket.CHUNK_BYTES,transfer.bytes.length));
        RequestHandle request=connection.request(new PacketFactory() {
            @Override public Packet create(int attempt) {
                return new MessageFragmentPacket(transfer.id,transfer.topic,index,transfer.parts,transfer.bytes.length,transfer.checksum,part);
            }
        },MessageFragmentAckPacket.class,transfer.policy,new ResponseCallback<MessageFragmentAckPacket>() {
            @Override public void done(MessageFragmentAckPacket result,Exception failure) {
                if(transfer.done.get()) return;
                if(failure!=null || result==null || !result.isAccepted() || !transfer.id.equals(result.getTransferID()) || result.getIndex()!=index) {
                    complete(transfer,failure!=null?failure:new IOException("El receptor no confirmó el fragmento")); return;
                }
                transfer.index++;
                try { transfer.callback.onProgress(Math.min(transfer.index*MessageFragmentPacket.CHUNK_BYTES,transfer.bytes.length),transfer.bytes.length); }
                catch(RuntimeException callbackFailure) { complete(transfer,callbackFailure); return; }
                if(transfer.index==transfer.parts) complete(transfer,null);
                else connection.dispatchCallback(new Runnable() { @Override public void run() { sendPart(transfer); } });
            }
        });
        synchronized(transfer) {
            if(transfer.done.get()) request.cancel(); else transfer.request=request;
        }
    }
    private boolean complete(final Transfer transfer,final Exception failure) {
        if(!transfer.done.compareAndSet(false,true)) return false;
        synchronized(this) {
            queue.remove(transfer); if(current==transfer) current=null;
            if(transfer.reserved) { reservedBytes-=transfer.bytes.length; transfer.reserved=false; }
        }
        connection.dispatchCallback(new Runnable() {
            @Override public void run() { try { transfer.callback.done(failure); } finally { next(); } }
        });
        return true;
    }
    @Override public void close() {
        Transfer[] all;
        synchronized(this) {
            closed=true; Queue<Transfer> pending=new ArrayDeque<Transfer>(queue);
            if(current!=null) pending.add(current);
            all=pending.toArray(new Transfer[0]);
        }
        for(Transfer transfer:all) transfer.cancel();
    }
    private final class Transfer implements RequestHandle {
        final String id=UUID.randomUUID().toString(), topic;
        final byte[] bytes,checksum;
        final int parts;
        final RequestOptions policy;
        final Callback callback;
        final AtomicBoolean done=new AtomicBoolean();
        volatile RequestHandle request;
        int index; boolean reserved;
        Transfer(String topic,byte[] bytes,RequestOptions policy,Callback callback) throws Exception {
            this.topic=topic; this.bytes=bytes; this.policy=policy; this.callback=callback;
            checksum=MessageDigest.getInstance("SHA-256").digest(bytes);
            parts=Math.max(1,(bytes.length+MessageFragmentPacket.CHUNK_BYTES-1)/MessageFragmentPacket.CHUNK_BYTES);
        }
        @Override public boolean cancel() {
            if(done.get()) return false;
            if(!complete(this,new CancellationException("Transferencia cancelada"))) return false;
            synchronized(this) { if(request!=null) request.cancel(); }
            return true;
        }
        @Override public boolean isDone() { return done.get(); }
    }
}
