package me.pepe.ServerClientAPI.Connections;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

/** TLS sobre NIO asíncrono; buffers acotados y ejecutores compartidos. */
public final class TlsSocketChannel extends AsynchronousSocketChannel {
    private static final int MAX_BUFFER = 65536;
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0).asReadOnlyBuffer();
    private static final ThreadPoolExecutor IO = pool(4, 4096, "server-client-tls");
    private static final ThreadPoolExecutor TASKS = pool(2, 256, "server-client-tls-task");
    private final AsynchronousSocketChannel raw;
    private final SSLEngine engine;
    private final Object lock = new Object();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final Queue<Runnable> notifications = new ArrayDeque<Runnable>();
    private ByteBuffer encryptedInput;
    private ByteBuffer encryptedOutput;
    private ByteBuffer plaintext;
    private boolean reading, writing, taskRunning, ready, closed, eof;
    private Operation<Integer> read, write;
    private Operation<Void> connect;
    private int written = -1;
    private ScheduledFuture<?> handshakeTimeout;

    private TlsSocketChannel(AsynchronousSocketChannel raw, SSLEngine engine) {
        super(AsynchronousChannelProvider.provider());
        this.raw = raw;
        this.engine = engine;
        encryptedInput = ByteBuffer.allocate(Math.min(MAX_BUFFER, engine.getSession().getPacketBufferSize()));
        encryptedOutput = ByteBuffer.allocate(encryptedInput.capacity());
        encryptedOutput.flip();
        plaintext = ByteBuffer.allocate(Math.min(MAX_BUFFER, engine.getSession().getApplicationBufferSize()));
    }

    public static TlsSocketChannel open(SSLContext context, String host, int port) throws IOException {
        SSLEngine engine = context.createSSLEngine(host, port);
        configure(engine, true);
        return new TlsSocketChannel(AsynchronousSocketChannel.open(), engine);
    }

    public static void accept(AsynchronousSocketChannel raw, SSLContext context,
            final CompletionHandler<TlsSocketChannel, Void> callback) throws IOException {
        SSLEngine engine = context.createSSLEngine();
        configure(engine, false);
        final TlsSocketChannel secure = new TlsSocketChannel(raw, engine);
        secure.begin(new Operation<Void>(null, new CompletionHandler<Void, Object>() {
            @Override public void completed(Void ignored, Object attachment) { callback.completed(secure, null); }
            @Override public void failed(Throwable failure, Object attachment) { callback.failed(failure, null); }
        }, null));
    }

    private static void configure(SSLEngine engine, boolean client) {
        engine.setUseClientMode(client);
        SSLParameters parameters = engine.getSSLParameters();
        parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        List<String> ciphers = new ArrayList<String>();
        for (String name : engine.getEnabledCipherSuites()) {
            if (name.startsWith("TLS_AES_") || name.startsWith("TLS_CHACHA20_")
                    || (name.startsWith("TLS_ECDHE_") && (name.contains("_GCM_") || name.contains("CHACHA20_POLY1305")))) ciphers.add(name);
        }
        parameters.setCipherSuites(ciphers.toArray(new String[ciphers.size()]));
        if (client) parameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(parameters);
    }

    private void begin(Operation<Void> operation) {
        synchronized (lock) {
            connect = operation;
            try {
                engine.beginHandshake();
                handshakeTimeout = ConnectionRuntime.schedule(new Runnable() {
                    @Override public void run() { fail(new SSLException("Tiempo de negociación TLS agotado.")); }
                }, 10000);
            } catch (IOException failure) { failLocked(failure); }
        }
        wake();
    }

    @Override public <A> void connect(final SocketAddress remote, final A attachment,
            final CompletionHandler<Void, ? super A> handler) {
        raw.connect(remote, attachment, new CompletionHandler<Void, A>() {
            @Override public void completed(Void ignored, A value) { begin(operation(null, attachment, handler)); }
            @Override public void failed(Throwable failure, A value) { fail(failure); handler.failed(failure, attachment); }
        });
    }
    @Override public Future<Void> connect(SocketAddress remote) {
        final CompletableFuture<Void> result = new CompletableFuture<Void>();
        connect(remote, result, futureHandler());
        return result;
    }

    @Override public <A> void read(ByteBuffer target, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        synchronized (lock) {
            if (read != null) throw new ReadPendingException();
            read = operation(target, attachment, handler);
            expire(read, timeout, unit);
        }
        wake();
    }
    @Override public Future<Integer> read(ByteBuffer target) {
        CompletableFuture<Integer> result = new CompletableFuture<Integer>();
        read(target, 0, TimeUnit.MILLISECONDS, result, futureHandler());
        return result;
    }
    @Override public <A> void write(ByteBuffer source, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        synchronized (lock) {
            if (write != null) throw new WritePendingException();
            write = operation(source, attachment, handler);
            expire(write, timeout, unit);
        }
        wake();
    }
    @Override public Future<Integer> write(ByteBuffer source) {
        CompletableFuture<Integer> result = new CompletableFuture<Integer>();
        write(source, 0, TimeUnit.MILLISECONDS, result, futureHandler());
        return result;
    }

    private void wake() {
        if (!queued.compareAndSet(false, true)) return;
        try {
            IO.execute(new Runnable() {
                @Override public void run() {
                    queued.set(false);
                    List<Runnable> callbacks;
                    synchronized (lock) {
                        try { drive(); } catch (Exception failure) { failLocked(failure); }
                        callbacks = new ArrayList<Runnable>(notifications);
                        notifications.clear();
                    }
                    for (Runnable callback : callbacks) {
                        try { callback.run(); } catch (RuntimeException ignored) { /* El consumidor gestiona su fallo. */ }
                    }
                }
            });
        } catch (RuntimeException failure) { queued.set(false); fail(failure); }
    }

    private void drive() throws IOException {
        if (closed) {
            if (read != null) { complete(read, null, new ClosedChannelException()); read = null; }
            if (write != null) { complete(write, null, new ClosedChannelException()); write = null; }
            return;
        }
        for (int step = 0; step < 64; step++) {
            if (writing) return;
            if (encryptedOutput.hasRemaining()) { flush(); return; }
            if (write != null && written >= 0) {
                complete(write, written, null); write = null; written = -1;
            }
            if (read != null && (plaintext.position() > 0 || !read.buffer.hasRemaining())) {
                plaintext.flip();
                int count = Math.min(plaintext.remaining(), read.buffer.remaining());
                int limit = plaintext.limit(); plaintext.limit(plaintext.position() + count);
                read.buffer.put(plaintext); plaintext.limit(limit); plaintext.compact();
                complete(read, count, null); read = null;
            }
            if (taskRunning) return;
            SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
            if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) { tasks(); return; }
            if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) { wrap(EMPTY.duplicate(), false); continue; }
            if (status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP
                    || status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {
                if (!unwrap()) return;
                continue;
            }
            if (!ready) {
                ready = true;
                if (handshakeTimeout != null) handshakeTimeout.cancel(false);
                complete(connect, null, null); connect = null;
            }
            if (eof) {
                if (read != null && plaintext.position() == 0) { complete(read, -1, null); read = null; }
                return;
            }
            if (!reading && encryptedInput.position() > 0 && plaintext.hasRemaining()) {
                if (unwrap()) continue;
            }
            if (write != null) { wrap(write.buffer, true); continue; }
            if (read != null && plaintext.position() == 0) receive();
            return;
        }
        wake();
    }

    private boolean unwrap() throws IOException {
        if (reading) return false;
        encryptedInput.flip();
        SSLEngineResult result;
        try { result = engine.unwrap(encryptedInput, plaintext); }
        finally { encryptedInput.compact(); }
        if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
            eof = true;
            if (!ready) throw new SSLException("TLS terminó antes de autenticar al servidor.");
            return true;
        }
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) { plaintext = grow(plaintext); return true; }
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            if (!encryptedInput.hasRemaining()) encryptedInput = grow(encryptedInput);
            receive(); return false;
        }
        return result.bytesConsumed() > 0 || result.bytesProduced() > 0
                || result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    private void wrap(ByteBuffer source, boolean application) throws IOException {
        encryptedOutput.clear();
        SSLEngineResult result = engine.wrap(source, encryptedOutput);
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            encryptedOutput = grow(encryptedOutput);
            encryptedOutput.flip();
            return;
        }
        encryptedOutput.flip();
        if (result.getStatus() == SSLEngineResult.Status.CLOSED) throw new SSLException("La salida TLS está cerrada.");
        if (application) written = result.bytesConsumed();
    }

    private ByteBuffer grow(ByteBuffer original) throws SSLException {
        if (original.capacity() >= MAX_BUFFER) throw new SSLException("TLS supera el límite de memoria por registro.");
        ByteBuffer larger = ByteBuffer.allocate(Math.min(MAX_BUFFER, original.capacity() * 2));
        original.flip(); larger.put(original); return larger;
    }

    private void receive() {
        if (reading || closed || eof) return;
        reading = true;
        raw.read(encryptedInput, null, new CompletionHandler<Integer, Void>() {
            @Override public void completed(Integer count, Void ignored) {
                synchronized (lock) {
                    reading = false;
                    if (count < 0) { failLocked(new SSLException("La conexión TLS se ha interrumpido.")); }
                }
                wake();
            }
            @Override public void failed(Throwable failure, Void ignored) { fail(failure); }
        });
    }

    private void flush() {
        if (writing || closed) return;
        writing = true;
        raw.write(encryptedOutput, null, new CompletionHandler<Integer, Void>() {
            @Override public void completed(Integer count, Void ignored) {
                synchronized (lock) {
                    writing = false;
                    if (count < 0) failLocked(new IOException("No se pudo escribir la conexión TLS."));
                }
                wake();
            }
            @Override public void failed(Throwable failure, Void ignored) { fail(failure); }
        });
    }

    private void tasks() {
        final List<Runnable> delegated = new ArrayList<Runnable>();
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) delegated.add(task);
        taskRunning = true;
        try {
            TASKS.execute(new Runnable() {
                @Override public void run() {
                    try { for (Runnable work : delegated) work.run(); }
                    catch (RuntimeException failure) { fail(failure); }
                    synchronized (lock) { taskRunning = false; }
                    wake();
                }
            });
        } catch (RuntimeException failure) { taskRunning = false; failLocked(failure); }
    }

    private void fail(Throwable failure) {
        List<Runnable> callbacks;
        synchronized (lock) {
            failLocked(failure);
            callbacks = new ArrayList<Runnable>(notifications); notifications.clear();
        }
        for (Runnable callback : callbacks) {
            try { callback.run(); } catch (RuntimeException ignored) { /* Completar también los demás consumidores. */ }
        }
    }
    private void failLocked(Throwable failure) {
        if (!closed) {
            closed = true;
            if (handshakeTimeout != null) handshakeTimeout.cancel(false);
            try { raw.close(); } catch (IOException ignored) {}
        }
        complete(connect, null, failure); connect = null;
        complete(read, null, failure); read = null;
        complete(write, null, failure); write = null;
    }
    private <T> void complete(final Operation<T> operation, final T value, final Throwable failure) {
        if (operation == null) return;
        if (operation.timeout != null) operation.timeout.cancel(false);
        notifications.add(new Runnable() {
            @Override public void run() {
                if (failure == null) operation.handler.completed(value, operation.attachment);
                else operation.handler.failed(failure, operation.attachment);
            }
        });
    }
    private void expire(Operation<?> operation, long timeout, TimeUnit unit) {
        if (timeout > 0) operation.timeout = ConnectionRuntime.schedule(new Runnable() {
            @Override public void run() { fail(new InterruptedByTimeoutException()); }
        }, Math.max(1, unit.toMillis(timeout)));
    }
    private static <T, A> Operation<T> operation(ByteBuffer buffer, A attachment, CompletionHandler<T, ? super A> handler) {
        return new Operation<T>(buffer, new CompletionHandler<T, Object>() {
            @SuppressWarnings("unchecked") @Override public void completed(T value, Object ignored) { handler.completed(value, attachment); }
            @Override public void failed(Throwable failure, Object ignored) { handler.failed(failure, attachment); }
        }, attachment);
    }
    private static <T> CompletionHandler<T, CompletableFuture<T>> futureHandler() {
        return new CompletionHandler<T, CompletableFuture<T>>() {
            @Override public void completed(T value, CompletableFuture<T> future) { future.complete(value); }
            @Override public void failed(Throwable failure, CompletableFuture<T> future) { future.completeExceptionally(failure); }
        };
    }
    private static final class Operation<T> {
        final ByteBuffer buffer;
        final CompletionHandler<T, Object> handler;
        final Object attachment;
        ScheduledFuture<?> timeout;
        Operation(ByteBuffer buffer, CompletionHandler<T, Object> handler, Object attachment) {
            this.buffer = buffer; this.handler = handler; this.attachment = attachment;
        }
    }
    private static ThreadPoolExecutor pool(int threads, int capacity, final String name) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(capacity), new ThreadFactory() {
            private int sequence;
            @Override public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task, name + "-" + ++sequence); thread.setDaemon(true); return thread;
            }
        });
    }

    public boolean isTlsReady() { synchronized (lock) { return ready && !closed; } }
    @Override public boolean isOpen() { return !closed && raw.isOpen(); }
    @Override public void close() { fail(new ClosedChannelException()); }
    @Override public SocketAddress getRemoteAddress() throws IOException { return raw.getRemoteAddress(); }
    @Override public SocketAddress getLocalAddress() throws IOException { return raw.getLocalAddress(); }
    @Override public <T> T getOption(SocketOption<T> option) throws IOException { return raw.getOption(option); }
    @Override public <T> AsynchronousSocketChannel setOption(SocketOption<T> option, T value) throws IOException { raw.setOption(option, value); return this; }
    @Override public Set<SocketOption<?>> supportedOptions() { return raw.supportedOptions(); }
    @Override public AsynchronousSocketChannel bind(SocketAddress address) throws IOException { raw.bind(address); return this; }
    @Override public AsynchronousSocketChannel shutdownInput() throws IOException { raw.shutdownInput(); return this; }
    @Override public AsynchronousSocketChannel shutdownOutput() throws IOException { close(); return this; }
    @Override public <A> void read(ByteBuffer[] targets, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        if (offset < 0 || length < 0 || offset > targets.length - length) throw new IndexOutOfBoundsException();
        for (int index = offset; index < offset + length; index++) {
            if (targets[index].hasRemaining()) {
                read(targets[index], timeout, unit, attachment, new CompletionHandler<Integer, A>() {
                    @Override public void completed(Integer count, A value) { handler.completed(count.longValue(), value); }
                    @Override public void failed(Throwable failure, A value) { handler.failed(failure, value); }
                }); return;
            }
        }
        handler.completed(0L, attachment);
    }
    @Override public <A> void write(ByteBuffer[] sources, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        if (offset < 0 || length < 0 || offset > sources.length - length) throw new IndexOutOfBoundsException();
        for (int index = offset; index < offset + length; index++) {
            if (sources[index].hasRemaining()) {
                write(sources[index], timeout, unit, attachment, new CompletionHandler<Integer, A>() {
                    @Override public void completed(Integer count, A value) { handler.completed(count.longValue(), value); }
                    @Override public void failed(Throwable failure, A value) { handler.failed(failure, value); }
                }); return;
            }
        }
        handler.completed(0L, attachment);
    }
}
