import ls.iqooaugment.com.core.DiagnosticPolicy;
import java.util.*;

/** Clipboard regression tests: every unapproved runtime field stays out. */
public final class DiagnosticsTests {
    static int checks;
    static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (String key : List.of("foreground", "lastAdmission", "nativeSupport", "model", "name",
                "label", "space", "mainUser", "serial", "pid", "process", "processUser", "version",
                "receivedElapsed", "lastAdmissionWall", "message", "featureState", "futureSensitiveField"))
            raw.put(key, "PRIVATE_CANARY");
        raw.put("ready", true); raw.put("hardware", false); raw.put("cleanupPending", true);
        raw.put("bridgeError", "PRIVATE_CANARY"); raw.put("profileError", "");
        Map<String, Object> safe = DiagnosticPolicy.summarize(raw);
        check(!safe.toString().contains("PRIVATE_CANARY"), "no runtime text copied");
        check(Boolean.TRUE.equals(safe.get("ready")), "safe booleans retained");
        check(Boolean.FALSE.equals(safe.get("hardware")), "false preserved");
        check(Boolean.TRUE.equals(safe.get("bridgeErrorPresent")), "error presence only");
        check(Boolean.FALSE.equals(safe.get("profileErrorPresent")), "empty error");
        check(Boolean.FALSE.equals(safe.get("observerErrorPresent")), "missing error");
        for (String key : raw.keySet()) if (!Set.of("ready", "hardware").contains(key))
            check(!safe.containsKey(key), "excluded field " + key);
        raw.put("ready", "PRIVATE_CANARY");
        check(!DiagnosticPolicy.summarize(raw).containsKey("ready"), "reject text in boolean field");
        raw.put("ready", Map.of("secret", "PRIVATE_CANARY"));
        check(!DiagnosticPolicy.summarize(raw).containsKey("ready"), "reject nested object");
        for (String key : List.of("fullFeatureHooks", "taskObserver", "taskObserverAvailable", "admissionHook")) {
            raw.put(key, true);
            check(Boolean.TRUE.equals(DiagnosticPolicy.summarize(raw).get(key)), "new boolean retained " + key);
            raw.put(key, false);
            check(Boolean.FALSE.equals(DiagnosticPolicy.summarize(raw).get(key)), "new false retained " + key);
            raw.put(key, "PRIVATE_CANARY");
            check(!DiagnosticPolicy.summarize(raw).containsKey(key), "new field text rejected " + key);
        }
        for (String key : List.of("driverObserver", "cleanupPending", "sessionActive", "driverSubmitted",
                "driverDisableSubmitted", "nativeListenerDetached", "lastDriverAttempt", "lastDriverAttemptWall",
                "recheckError", "nativeSupport", "profileProbe")) {
            raw.put(key, true);
            check(!DiagnosticPolicy.summarize(raw).containsKey(key), "obsolete ownership diagnostic omitted " + key);
        }
        check(!DiagnosticPolicy.summarize(raw).containsKey("recheckErrorPresent"), "obsolete replay error omitted");
        raw.put("featureState", "native_owned");
        check(!DiagnosticPolicy.summarize(raw).containsKey("featureState"), "clipboard remains boolean-only");
        System.out.println("PASS: " + checks + " diagnostic minimization assertions");
    }
}
