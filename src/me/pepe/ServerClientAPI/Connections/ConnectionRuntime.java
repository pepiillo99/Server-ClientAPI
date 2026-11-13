package me.pepe.ServerClientAPI.Connections;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Recursos compartidos; ninguna conexión necesita un hilo de vigilancia propio. */
public final class ConnectionRuntime {
    private static final Set<ClientConnection> CONNECTIONS = ConcurrentHashMap.newKeySet();
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(2, factory("server-client-timer"));
    private static final ExecutorService CALLBACKS = Executors.newFixedThreadPool(8, factory("server-client-callback"));
    static {
        TIMER.setRemoveOnCancelPolicy(true);
        TIMER.scheduleWithFixedDelay(new Runnable() {
            @Override public void run() {
                long now = System.nanoTime();
                for (ClientConnection connection : CONNECTIONS) {
                    try { connection.maintenance(now); }
                    catch (RuntimeException failure) { connection.log("Error en la supervisión de conexión: " + failure.getClass().getSimpleName()); }
                }
            }
        }, 250, 250, TimeUnit.MILLISECONDS);
    }
    private ConnectionRuntime() {}
    static void track(ClientConnection connection) { CONNECTIONS.add(connection); }
    static void untrack(ClientConnection connection) { CONNECTIONS.remove(connection); }
    public static int getTrackedConnectionCount() { return CONNECTIONS.size(); }
    public static ScheduledFuture<?> schedule(Runnable task, long delayMillis) {
        return TIMER.schedule(task, Math.max(0,delayMillis), TimeUnit.MILLISECONDS);
    }
    static void executeCallback(Runnable task) { CALLBACKS.execute(task); }
    private static ThreadFactory factory(final String name) {
        return new ThreadFactory() {
            private int sequence;
            @Override public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task,name + "-" + ++sequence);
                thread.setDaemon(true); return thread;
            }
        };
    }
}
