package ls.iqooaugment.com.core;

/** Synchronous category permission only. Never owns native input, output, listeners or storage. */
public final class NativeCategoryScope {
    public static final class Frame {
        public final AppKey key;
        public final Object evidence;
        private final Frame previous;
        private final boolean cacheOnly;
        private Frame(AppKey key, Object evidence, Frame previous, boolean cacheOnly) {
            this.key = key; this.evidence = evidence; this.previous = previous;
            this.cacheOnly = cacheOnly;
        }
    }
    private final ThreadLocal<Frame> current = new ThreadLocal<>();
    public Frame enter(AppKey key, Object evidence) {
        return enter(key, evidence, false);
    }
    public Frame enter(AppKey key, Object evidence, boolean cacheOnly) {
        Frame frame = new Frame(key, evidence, current.get(), cacheOnly || cacheOnly());
        current.set(frame); return frame;
    }
    public boolean cacheOnly() { Frame frame = current.get(); return frame != null && frame.cacheOnly; }
    public Frame current() { return current.get(); }
    public boolean allows(String packageName, Object evidence) {
        Frame frame = current.get();
        return frame != null && frame.key != null && frame.evidence == evidence
            && frame.key.packageName.equals(packageName);
    }
    public void leave(Frame frame) {
        if (frame == null) return;
        if (current.get() != frame) throw new IllegalStateException("Unbalanced native category scope");
        if (frame.previous == null) current.remove(); else current.set(frame.previous);
    }
}
