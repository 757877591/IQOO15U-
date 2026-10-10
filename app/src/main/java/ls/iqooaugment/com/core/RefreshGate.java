package ls.iqooaugment.com.core;

/** Coalesces real events and owns a finite retry series. No clock or automatic polling. */
public final class RefreshGate {
    private static final long[] DELAYS = {250, 1_000, 4_000};
    private Object series;
    private boolean pending;
    private int retries;
    public synchronized boolean request() {
        pending = true;
        if (series != null) return false;
        series = new Object(); retries = 0; return true;
    }
    public synchronized Object begin() { pending = false; return series; }
    /** Death cancels queued and in-flight retries; completion cannot revive a cancelled series. */
    public synchronized void cancel() { series = null; pending = false; retries = 0; }
    /** A negative delay ends this series; an event during a call is never lost. */
    public synchronized long complete(Object attempt, boolean failed) {
        if (attempt == null || attempt != series) return -1;
        if (pending) { pending = false; retries = 0; return 0; }
        if (failed && retries < DELAYS.length) return DELAYS[retries++];
        series = null; return -1;
    }
    /** Use under the connection's publication lock: tokens cannot wrap or be re-used. */
    public static final class Epoch {
        private Object current = new Object();
        public Object capture() { return current; }
        public void invalidate() { current = new Object(); }
        public boolean matches(Object token) { return token == current; }
    }
}
