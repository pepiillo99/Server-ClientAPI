package me.pepe.ServerClientAPI.Connections;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import me.pepe.ServerClientAPI.Packet;
import me.pepe.ServerClientAPI.Utils.AwaitAnswerCallback;
import me.pepe.ServerClientAPI.Utils.PacketFactory;
import me.pepe.ServerClientAPI.Utils.RequestHandle;
import me.pepe.ServerClientAPI.Utils.RequestOptions;
import me.pepe.ServerClientAPI.Utils.ResponseCallback;

final class PacketRequest<T extends Packet> implements RequestHandle {
    private final ClientConnection connection;
    private final PacketFactory factory;
    private final Class<T> type;
    private final RequestOptions options;
    private final ResponseCallback<T> callback;
    private final AtomicBoolean done=new AtomicBoolean();
    private final Set<Packet> used=Collections.newSetFromMap(new IdentityHashMap<Packet,Boolean>());
    private int attempt;
    private Packet current;
    private AwaitAnswerCallback awaiting;
    private ScheduledFuture<?> retry;

    PacketRequest(ClientConnection connection,PacketFactory factory,Class<T> type,RequestOptions options,ResponseCallback<T> callback) {
        this.connection=connection; this.factory=factory; this.type=type; this.options=options; this.callback=callback;
    }
    void start() {
        final int number;
        synchronized(this) { if(done.get()) return; number=++attempt; }
        Packet packet;
        try { packet=factory.create(number); }
        catch(Exception failure) { finish(null,failure); return; }
        synchronized(this) {
            if(done.get()) return;
            if(packet==null || !used.add(packet)) { finish(null,new IOException("La factoría debe crear un paquete nuevo por intento")); return; }
            current=packet;
            awaiting=new AwaitAnswerCallback(connection,options.getTimeoutMillis(),type) {
                @Override public void onAnswer(Packet response) {
                    if(type.isInstance(response)) finishAttempt(number,type.cast(response),null);
                    else finishAttempt(number,null,new IOException("La respuesta no tiene el tipo esperado"));
                }
                @Override public void onTimeOut() { onFailure(new SocketTimeoutException("Tiempo de respuesta agotado")); }
                @Override public void onFailure(Exception failure) { finishAttempt(number,null,failure); }
            };
            packet.expectResponse(awaiting);
            connection.sendPacket(packet);
        }
    }
    private synchronized void finishAttempt(int number,T result,Exception failure) {
        if(done.get() || number!=attempt) return;
        if(failure instanceof SocketTimeoutException && attempt<options.getMaxAttempts() && connection.isConnected()) {
            retry=ConnectionRuntime.schedule(new Runnable() {
                @Override public void run() {
                    connection.executeCallback(new Runnable() { @Override public void run() { start(); } });
                }
            },options.getRetryDelayMillis());
        } else finish(result,failure);
    }
    private void finish(final T result,final Exception failure) {
        if(!done.compareAndSet(false,true)) return;
        synchronized(this) { if(retry!=null) retry.cancel(false); }
        connection.executeCallback(new Runnable() { @Override public void run() { callback.done(result,failure); } });
    }
    @Override public boolean cancel() {
        if(!done.compareAndSet(false,true)) return false;
        synchronized(this) {
            if(retry!=null) retry.cancel(false);
            if(current!=null) connection.cancelRequest(current,awaiting);
        }
        connection.executeCallback(new Runnable() {
            @Override public void run() { callback.done(null,new CancellationException("Petición cancelada")); }
        });
        return true;
    }
    @Override public boolean isDone() { return done.get(); }
}
