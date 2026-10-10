package ls.iqooaugment.com.hook;

import android.content.Context;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.data.Config;

/** One current eligibility observation, not a native shoulder session or input authorization lease. */
final class SelectedEligibility {
    static final class Selection {
        final AppKey key;
        final Config config;
        final long generation;
        Selection(AppKey key, Config config, long generation) {
            this.key = key; this.config = config; this.generation = generation;
        }
    }
    private final BridgeClient bridge;
    private final ForegroundResolver resolver;
    private final BooleanSupplier enabled;
    private final AtomicLong generation = new AtomicLong();
    private volatile Selection cached;
    SelectedEligibility(Context context, BridgeClient bridge, BooleanSupplier enabled) {
        this.bridge = bridge; this.enabled = enabled;
        resolver = new ForegroundResolver(context, () -> enabled.getAsBoolean() && bridge.current().master);
    }
    void invalidate() { generation.incrementAndGet(); cached = null; }
    Selection resolve(String expectedPackage) {
        Config config = bridge.current();
        long observed = generation.get();
        if (!enabled.getAsBoolean() || !config.master) return null;
        // No selected package: avoid an unnecessary foreground/package-manager probe.
        if (expectedPackage != null && config.enabled.stream().noneMatch(k -> k.packageName.equals(expectedPackage))) return null;
        try {
            AppKey key = resolver.resolve(expectedPackage);
            if (key == null || !config.enabled.contains(key) || !resolver.eligible(key)) return null;
            Selection result = new Selection(key, config, observed);
            if (!valid(result)) return null;
            cached = result;
            return result;
        } catch (Throwable error) { return null; }
    }
    Selection peek(String expectedPackage) {
        Selection result = cached;
        return valid(result) && result.key.packageName.equals(expectedPackage) ? result : null;
    }
    boolean valid(Selection result) {
        return result != null && enabled.getAsBoolean() && generation.get() == result.generation
            && bridge.current() == result.config && result.config.master && result.config.enabled.contains(result.key);
    }
}
