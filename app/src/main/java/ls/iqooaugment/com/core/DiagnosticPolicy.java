package ls.iqooaugment.com.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Positive allowlist: never pass arbitrary runtime text or identifiers to the clipboard. */
public final class DiagnosticPolicy {
    private DiagnosticPolicy() { }
    public static Map<String, Object> summarize(Map<String, ?> status) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : new String[]{"ready", "hardware", "admissionHook", "taskObserver",
                "taskObserverAvailable", "master", "configPending", "fullFeatureHooks"}) {
            Object value = status.get(key);
            if (value instanceof Boolean) out.put(key, value);
        }
        for (String key : new String[]{"bridgeError", "profileError", "observerError"}) {
            Object value = status.get(key);
            out.put(key + "Present", value != null && !String.valueOf(value).isEmpty());
        }
        return out;
    }
}
