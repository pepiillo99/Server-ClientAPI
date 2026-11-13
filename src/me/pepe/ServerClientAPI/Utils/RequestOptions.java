package me.pepe.ServerClientAPI.Utils;

/** Los reintentos se activan expresamente para operaciones idempotentes. */
public final class RequestOptions {
    private final int maxAttempts;
    private final long timeoutMillis, retryDelayMillis;
    public RequestOptions(long timeoutMillis) { this(1,timeoutMillis,0); }
    public RequestOptions(int maxAttempts,long timeoutMillis,long retryDelayMillis) {
        if (maxAttempts < 1 || timeoutMillis < 1 || retryDelayMillis < 0)
            throw new IllegalArgumentException("Política de petición no válida");
        this.maxAttempts=maxAttempts; this.timeoutMillis=timeoutMillis; this.retryDelayMillis=retryDelayMillis;
    }
    public int getMaxAttempts() { return maxAttempts; }
    public long getTimeoutMillis() { return timeoutMillis; }
    public long getRetryDelayMillis() { return retryDelayMillis; }
}
