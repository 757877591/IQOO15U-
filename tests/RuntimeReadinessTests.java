import ls.iqooaugment.com.core.RuntimeReadiness;
import java.util.*;

/** Live eligibility-hook coherence; never evidence of OEM mapping or physical touch output. */
public final class RuntimeReadinessTests {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++; if (!value) throw new AssertionError(name);
    }
    private static Map<String, Object> state(boolean master, long revision) {
        Map<String, Object> result = new HashMap<>();
        result.put("ready", true); result.put("hardware", true);
        result.put("admissionHook", true); result.put("fullFeatureHooks", true);
        result.put("configPending", false); result.put("master", master);
        result.put("configRevision", revision); result.put("bridgeError", "");
        result.put("taskObserver", master);
        return result;
    }
    private static boolean combined(boolean master, Map<String, ?> daemon, Map<String, ?> assistant) {
        return RuntimeReadiness.ready(true, true, master, 12, daemon, assistant);
    }
    public static void main(String[] args) {
        Map<String, Object> daemon = state(false, 12), assistant = state(false, 12);
        check(combined(false, daemon, assistant), "paused capability reports permit pre-enable");
        check(RuntimeReadiness.assistantReady(true, false, 12, assistant), "assistant initializes independently of daemon");
        daemon.remove("fullFeatureHooks"); assistant.remove("admissionHook");
        check(combined(false, daemon, assistant), "process-specific hooks independent");
        check(!RuntimeReadiness.ready(false, true, false, 12, daemon, assistant), "dead daemon publisher rejected immediately");
        check(!RuntimeReadiness.ready(true, false, false, 12, daemon, assistant), "dead assistant publisher rejected immediately");
        check(!RuntimeReadiness.ready(false, false, false, 12, daemon, assistant), "dead publishers rejected");
        check(!RuntimeReadiness.assistantReady(false, false, 12, assistant), "obsolete assistant publisher generation rejected");
        for (boolean enabled : new boolean[]{false, true}) {
            for (boolean assistantRole : new boolean[]{false, true}) {
                Map<String, Object> good = state(enabled, 12);
                for (String key : List.of("ready", "hardware", assistantRole ? "fullFeatureHooks" : "admissionHook")) {
                    for (Object bad : Arrays.asList(false, null, "true", 1, Collections.singletonMap("flag", true))) {
                        Map<String, Object> report = new HashMap<>(good); report.put(key, bad);
                        boolean ready = assistantRole
                            ? RuntimeReadiness.assistantReady(true, enabled, 12, report)
                            : RuntimeReadiness.daemonReady(true, enabled, 12, report);
                        check(!ready, "missing, false or mistyped capability rejected " + key);
                    }
                }
                for (Object bad : Arrays.asList(true, null, "false", 0)) {
                    Map<String, Object> report = new HashMap<>(good); report.put("configPending", bad);
                    check(!RuntimeReadiness.currentConfig(enabled, 12, report), "pending or mistyped configuration rejected");
                }
                for (Object revision : Arrays.asList(11L, 13L, -1L, null, "12", 12.0, 4294967308L)) {
                    Map<String, Object> report = new HashMap<>(good); report.put("configRevision", revision);
                    check(!RuntimeReadiness.currentConfig(enabled, 12, report), "revision matches exactly without coercion");
                    check(!RuntimeReadiness.assistantReady(true, enabled, 12, report), "revision mismatch cannot admit target");
                }
                for (Object master : Arrays.asList(!enabled, null, "" + enabled, 1)) {
                    Map<String, Object> report = new HashMap<>(good); report.put("master", master);
                    check(!RuntimeReadiness.currentConfig(enabled, 12, report), "master matches exactly without coercion");
                }
                for (Object error : List.of("bridge failure", 1, true)) {
                    Map<String, Object> report = new HashMap<>(good); report.put("bridgeError", error);
                    check(!RuntimeReadiness.assistantReady(true, enabled, 12, report), "bridge error rejects readiness");
                }
            }
        }
        daemon = state(true, 12); assistant = state(true, 12);
        check(combined(true, daemon, assistant), "enabled live coherent hooks ready");
        for (long timestamp : new long[]{-1, 0, 1, 90_000, Long.MAX_VALUE}) {
            daemon.put("receivedElapsed", timestamp); assistant.put("receivedElapsed", timestamp);
            check(combined(true, daemon, assistant), "receipt age does not force renewal or expire readiness");
        }
        for (String legacy : List.of("taskObserverAvailable", "cleanupPending", "sessionActive", "driverSubmitted")) {
            daemon.put(legacy, false); assistant.put(legacy, false);
            check(combined(true, daemon, assistant), "native lifecycle is not a module-owned capability: " + legacy);
        }
        daemon.remove("taskObserver");
        check(combined(true, daemon, assistant), "daemon requires no task observer");
        for (Object observer : Arrays.asList(false, null, "true", 1)) {
            assistant.put("taskObserver", observer);
            check(!combined(true, daemon, assistant), "enabled assistant requires typed running eligibility observer");
            check(!RuntimeReadiness.assistantReady(true, true, 12, assistant), "observer start failure revokes assistant readiness");
            check(RuntimeReadiness.daemonReady(true, true, 12, daemon), "assistant observer failure does not fabricate daemon watcher requirement");
        }
        assistant.put("taskObserver", true);
        check(combined(true, daemon, assistant), "explicit observer recovery restores coherent readiness");
        Map<String, Object> paused = state(false, 12);
        paused.remove("taskObserver");
        check(RuntimeReadiness.assistantReady(true, false, 12, paused), "paused configuration does not start observer to establish capability");
        assistant.put("configRevision", 11L);
        check(!combined(true, daemon, assistant), "old configuration remains rejected despite live Binder");
        assistant.put("configRevision", 12L); assistant.put("configPending", true);
        check(!combined(true, daemon, assistant), "provider master canonicalization cannot hide pending config");
        assistant.put("configPending", false); assistant.put("fullFeatureHooks", false);
        check(!combined(true, daemon, assistant), "missing precise category hooks blocks admission");
        check(!RuntimeReadiness.assistantReady(true, true, 12, null), "missing assistant fails closed");
        check(!RuntimeReadiness.daemonReady(true, true, 12, Collections.emptyMap()), "empty daemon fails closed");
        check(!RuntimeReadiness.currentConfig(true, -1, state(true, -1)), "negative current revision rejected");
        assistant.put("configRevision", 12);
        check(RuntimeReadiness.currentConfig(true, 12, assistant), "integer JSON revision accepted exactly");
        System.out.println("PASS: " + checks + " live event-only readiness/coherence assertions");
    }
}
