package ls.iqooaugment.com.core;

/** Bounded automatic cleanup retry budget. Exhaustion leaves authority revoked, never restores it. */
public final class CleanupRetries {
    private static final long[] DELAYS_MS = {250, 1_000, 4_000};
    private Object owner;
    private int attempts;
    public synchronized long failed(Object token) {
        if (token == null) return -1;
        if (owner != token) { owner = token; attempts = 0; }
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts++] : -1;
    }
    public synchronized void completed(Object token) {
        if (owner == token) { owner = null; attempts = 0; }
    }
}
