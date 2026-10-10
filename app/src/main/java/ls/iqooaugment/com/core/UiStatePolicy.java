package ls.iqooaugment.com.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure recovery rules; this retains user choices, never Root authorization or application data. */
public final class UiStatePolicy {
    private UiStatePolicy() { }

    /** UI-thread gate for reads posted before a newer request or an intervening save. */
    public static final class ConfigReads {
        private long generation;
        private boolean saving;
        public long request() { return ++generation; }
        public void beginSave() { saving = true; generation++; }
        public void endSave() { saving = false; }
        public boolean accepts(long request) { return !saving && request == generation; }
    }

    /** Reuse the whole consented scope, including spaces with no picked applications. */
    public static Set<ScanPolicy.SpaceKey> recoveryScope(Collection<String> picked,
            Collection<ScanPolicy.SpaceKey> consented) {
        Set<ScanPolicy.SpaceKey> scope = new LinkedHashSet<>(consented);
        Set<ScanPolicy.SpaceKey> required = new LinkedHashSet<>();
        for (String value : picked) {
            AppKey key = AppKey.decode(value);
            required.add(new ScanPolicy.SpaceKey(key.userId, key.serial));
        }
        // Earlier Activity state did not record its scan scope. Explicitly reconfirm only
        // the draft's own space identities; never guess another user or a new serial.
        if (scope.isEmpty()) scope.addAll(required);
        if (!scope.containsAll(required)) throw new IllegalArgumentException("草稿与原读取范围不一致，请检查草稿空间身份");
        ScanPolicy.encodeSelection(scope);
        return scope;
    }

    /** The caller offers explicit removal; failed verification never silently drops choices. */
    public static Set<String> unavailableDraft(Collection<String> picked,
            Collection<String> eligible, Collection<String> saved) {
        Set<String> usable = new LinkedHashSet<>(eligible);
        usable.removeAll(saved);
        Set<String> unavailable = new LinkedHashSet<>(picked);
        unavailable.removeAll(usable);
        return unavailable;
    }
}
