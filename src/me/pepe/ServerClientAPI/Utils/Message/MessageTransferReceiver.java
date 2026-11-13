package me.pepe.ServerClientAPI.Utils.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import me.pepe.ServerClientAPI.Connections.ClientConnection;
import me.pepe.ServerClientAPI.Connections.ConnectionRuntime;
import me.pepe.ServerClientAPI.Utils.ResponseCallback;

public final class MessageTransferReceiver implements AutoCloseable {
    public interface Handler { void receive(String topic,byte[] payload,ResponseCallback<Boolean> saved); }
    private final int maxBytes, maxActive;
    private final long timeoutNanos;
    private final Map<String,Session> active=new HashMap<String,Session>();
    private final Map<String,Completed> completed=new LinkedHashMap<String,Completed>();
    private ScheduledFuture<?> cleanup;
    private boolean closed;
    public MessageTransferReceiver(int maxBytes,int maxActive,long timeoutMillis) {
        if(maxBytes<1 || maxActive<1 || timeoutMillis<1) throw new IllegalArgumentException("Límites de recepción no válidos");
        this.maxBytes=maxBytes; this.maxActive=maxActive; timeoutNanos=TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }
    public void receive(final ClientConnection connection,final MessageFragmentPacket packet,final Handler handler) {
        Session finalSession=null;
        byte[] message=null;
        boolean answerNow=true, accepted=false;
        try {
            synchronized(this) {
                pruneCompleted(System.nanoTime());
                validate(packet);
                if(closed) { answerNow=true; }
                else if(completed.containsKey(packet.getTransferID())) {
                    accepted=completed.get(packet.getTransferID()).matches(packet);
                } else {
                    Session session=active.get(packet.getTransferID());
                    if(session==null) {
                        if(packet.getIndex()!=0 || active.size()>=maxActive) {
                            answerNow=true;
                        } else {
                            session=new Session(packet); active.put(packet.getTransferID(),session);
                        }
                    }
                    if(session!=null) {
                        if(!session.matches(packet)) throw new IOException("La cabecera de transferencia ha cambiado");
                        byte[] bytes=packet.getPayload();
                        if(session.parts[packet.getIndex()]!=null && !Arrays.equals(session.parts[packet.getIndex()],bytes))
                            throw new IOException("El fragmento duplicado es distinto");
                        if(session.parts[packet.getIndex()]==null) {
                            session.parts[packet.getIndex()]=bytes; session.received++;
                        }
                        session.lastActivity=System.nanoTime();
                        if(session.received<session.parts.length) accepted=true;
                        else {
                            if(session.waiters.size()>=8) throw new IOException("Demasiadas confirmaciones pendientes");
                            session.waiters.add(new Waiter(connection,packet)); answerNow=false;
                            if(!session.saving) {
                                session.saving=true;
                                ByteArrayOutputStream output=new ByteArrayOutputStream(session.total);
                                for(byte[] part:session.parts) output.write(part);
                                message=output.toByteArray();
                                if(message.length!=session.total || !MessageDigest.isEqual(session.checksum,MessageDigest.getInstance("SHA-256").digest(message)))
                                    throw new IOException("No coincide la integridad del mensaje");
                                finalSession=session;
                            }
                        }
                        scheduleCleanup();
                    }
                }
            }
        } catch(Exception failure) {
            Session rejected;
            synchronized(this) { rejected=active.remove(packet.getTransferID()); }
            boolean answeredCurrent=false;
            if(rejected!=null) for(Waiter waiter:rejected.waiters) {
                waiter.answer(false); if(waiter.packet==packet) answeredCurrent=true;
            }
            answerNow=!answeredCurrent; accepted=false;
        }
        if(answerNow) { new Waiter(connection,packet).answer(accepted); return; }
        if(finalSession==null) return;
        final Session session=finalSession;
        final AtomicBoolean answered=new AtomicBoolean();
        final ResponseCallback<Boolean> saved=new ResponseCallback<Boolean>() {
            @Override public void done(Boolean result,Exception failure) {
                if(!answered.compareAndSet(false,true)) return;
                finish(session,failure==null && Boolean.TRUE.equals(result));
            }
        };
        try { handler.receive(session.topic,message,saved); }
        catch(Exception failure) { saved.done(false,failure); }
    }
    private void validate(MessageFragmentPacket packet) throws IOException {
        try { UUID.fromString(packet.getTransferID()); } catch(Exception failure) { throw new IOException("Referencia de transferencia no válida"); }
        int expectedParts=Math.max(1,(packet.getTotalBytes()+MessageFragmentPacket.CHUNK_BYTES-1)/MessageFragmentPacket.CHUNK_BYTES);
        if(packet.getTopic()==null || packet.getTopic().isEmpty() || packet.getTopic().length()>128
                || packet.getTotalBytes()<0 || packet.getTotalBytes()>maxBytes || packet.getParts()!=expectedParts
                || packet.getIndex()<0 || packet.getIndex()>=expectedParts || packet.getChecksum().length!=32)
            throw new IOException("Límites de transferencia no válidos");
        int expected=Math.min(MessageFragmentPacket.CHUNK_BYTES,packet.getTotalBytes()-packet.getIndex()*MessageFragmentPacket.CHUNK_BYTES);
        if(packet.getPayload().length!=expected) throw new IOException("Longitud de fragmento no válida");
    }
    private void finish(Session session,boolean accepted) {
        List<Waiter> waiters;
        synchronized(this) {
            if(active.get(session.id)!=session) return;
            active.remove(session.id); waiters=new ArrayList<Waiter>(session.waiters);
            if(accepted) {
                while(completed.size()>=64) completed.remove(completed.keySet().iterator().next());
                completed.put(session.id,new Completed(session));
            }
        }
        for(Waiter waiter:waiters) waiter.answer(accepted);
    }
    private void scheduleCleanup() {
        if(cleanup!=null || closed) return;
        cleanup=ConnectionRuntime.schedule(new Runnable() {
            @Override public void run() {
                List<Session> expired=new ArrayList<Session>();
                synchronized(MessageTransferReceiver.this) {
                    cleanup=null; long now=System.nanoTime();
                    for(Session session:active.values()) if(now-session.lastActivity>=timeoutNanos) expired.add(session);
                }
                for(Session session:expired) finish(session,false);
                synchronized(MessageTransferReceiver.this) { if(!active.isEmpty()) scheduleCleanup(); }
            }
        },Math.max(1,TimeUnit.NANOSECONDS.toMillis(timeoutNanos)));
    }
    private void pruneCompleted(long now) {
        Iterator<Completed> iterator=completed.values().iterator();
        while(iterator.hasNext()) if(now-iterator.next().created>=timeoutNanos) iterator.remove();
    }
    @Override public void close() {
        List<Session> sessions;
        synchronized(this) {
            closed=true; if(cleanup!=null) cleanup.cancel(false);
            sessions=new ArrayList<Session>(active.values()); completed.clear();
        }
        for(Session session:sessions) finish(session,false);
    }
    private static class Session {
        final String id,topic;
        final int total;
        final byte[] checksum;
        final byte[][] parts;
        final List<Waiter> waiters=new ArrayList<Waiter>();
        int received; boolean saving; long lastActivity=System.nanoTime();
        Session(MessageFragmentPacket packet) {
            id=packet.getTransferID(); topic=packet.getTopic(); total=packet.getTotalBytes();
            checksum=packet.getChecksum(); parts=new byte[packet.getParts()][];
        }
        boolean matches(MessageFragmentPacket packet) {
            return total==packet.getTotalBytes() && parts.length==packet.getParts() && topic.equals(packet.getTopic())
                && MessageDigest.isEqual(checksum,packet.getChecksum());
        }
    }
    private static final class Completed {
        final String topic; final int total,parts; final byte[] checksum; final long created=System.nanoTime();
        Completed(Session session) { topic=session.topic; total=session.total; parts=session.parts.length; checksum=session.checksum; }
        boolean matches(MessageFragmentPacket packet) {
            return total==packet.getTotalBytes() && parts==packet.getParts() && topic.equals(packet.getTopic())
                && MessageDigest.isEqual(checksum,packet.getChecksum());
        }
    }
    private static final class Waiter {
        final ClientConnection connection; final MessageFragmentPacket packet;
        Waiter(ClientConnection connection,MessageFragmentPacket packet) { this.connection=connection; this.packet=packet; }
        void answer(boolean accepted) { packet.answer(connection,new MessageFragmentAckPacket(packet.getTransferID(),packet.getIndex(),accepted)); }
    }
}
