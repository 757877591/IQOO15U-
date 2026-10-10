import ls.iqooaugment.com.core.ProviderAccessPolicy;
import java.util.Collections;

/** Host-side policy tests; these do not prove OEM Binder or process authentication. */
public final class ProviderAccessTests {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    private static void rejectsCategory(String value) {
        boolean rejected = false;
        try { ProviderAccessPolicy.statusCategory(value); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Reject category " + value);
    }
    public static void main(String[] args) {
        final int own = 10123;
        check(ProviderAccessPolicy.canReadConfig(own, own), "exact own UID can read");
        check(!ProviderAccessPolicy.canPublishStatus(own), "self cannot claim Hook status");
        for (int user = 0; user < 100; user++) {
            int systemUid = user * 100000 + 1000;
            check(ProviderAccessPolicy.canReadConfig(systemUid, own), "system shared UID read");
            check(ProviderAccessPolicy.canPublishStatus(systemUid), "system shared UID status");
            check(!ProviderAccessPolicy.canReadConfig(systemUid + 1, own), "adjacent UID denied");
            check(!ProviderAccessPolicy.canPublishStatus(systemUid + 1), "adjacent status denied");
        }
        for (int denied : new int[]{-1, Integer.MIN_VALUE, 0, 2000, 10000, 11111, 110123}) {
            check(!ProviderAccessPolicy.canReadConfig(denied, own), "untrusted UID read denied");
            check(!ProviderAccessPolicy.canPublishStatus(denied), "untrusted UID status denied");
        }
        check(!ProviderAccessPolicy.canReadConfig(-1, -1), "invalid self UID denied");
        check("daemon".equals(ProviderAccessPolicy.statusCategory("com.vivo.gamecube:daemon")), "daemon category");
        check("assistantui".equals(ProviderAccessPolicy.statusCategory("com.vivo.gamecube:assistantui")), "assistant category");
        for (String value : new String[]{null, "", "com.vivo.gamecube", "com.vivo.gamecube:daemon:extra", "evil:daemon", "com.vivo.gamecube:daemon\n"}) rejectsCategory(value);
        check(ProviderAccessPolicy.matchingDiagnosticPid(1234, 1234), "matching int PID");
        check(ProviderAccessPolicy.matchingDiagnosticPid(1234, 1234L), "matching long PID");
        for (Object value : new Object[]{null, "1234", 1234.0, 1235, 4294968530L, -1})
            check(!ProviderAccessPolicy.matchingDiagnosticPid(1234, value), "invalid or mismatched PID");
        check(!ProviderAccessPolicy.matchingDiagnosticPid(0, 0), "absent binder PID");
        check(!ProviderAccessPolicy.matchingDiagnosticPid(-1, -1), "negative binder PID");
        check(ProviderAccessPolicy.discloseTargetToHook(true, true), "active enabled target disclosed");
        check(!ProviderAccessPolicy.discloseTargetToHook(false, true), "paused config conceals targets");
        check(!ProviderAccessPolicy.discloseTargetToHook(true, false), "disabled targets concealed");
        check(!ProviderAccessPolicy.discloseTargetToHook(false, false), "paused disabled targets concealed");
        check(ProviderAccessPolicy.matchesCurrentConfig(true, 12, true, 12L), "current active report matches");
        check(ProviderAccessPolicy.mayRetainActivity(true, 12, true, 12L), "current active details retained");
        check(ProviderAccessPolicy.matchesCurrentConfig(false, 11, false, 11), "current paused report matches");
        check(!ProviderAccessPolicy.mayRetainActivity(false, 11, false, 11), "paused details always redacted");
        check(!ProviderAccessPolicy.mayRetainActivity(true, 12, true, 10L), "pre-disable report cannot restore details after re-enable");
        check(!ProviderAccessPolicy.matchesCurrentConfig(true, 12, false, 11L), "paused old report remains pending after re-enable");
        check(!ProviderAccessPolicy.matchesCurrentConfig(true, 12, false, 12L), "matching revision with wrong master remains pending");
        check(!ProviderAccessPolicy.matchesCurrentConfig(false, 11, true, 11L), "enabled report while paused remains pending");
        check(!ProviderAccessPolicy.mayRetainActivity(true, 13, true, 12L), "selection-edit stale report redacted");
        check(!ProviderAccessPolicy.matchesCurrentConfig(true, -1, true, -1L), "negative current revision rejected");
        for (Object value : new Object[]{null, "12", 12.0, -1L, 4294967308L})
            check(!ProviderAccessPolicy.matchesCurrentConfig(true, 12, true, value), "missing mistyped or different revision pending");
        for (Object value : new Object[]{null, "true", 1})
            check(!ProviderAccessPolicy.matchesCurrentConfig(true, 12, value, 12L), "missing or mistyped master pending");
        check(ProviderAccessPolicy.isAllowedDiagnosticValue("ready", true), "typed boolean");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("ready", "true"), "string boolean rejected");
        for (String key : new String[]{"fullFeatureHooks", "taskObserver", "taskObserverAvailable", "admissionHook"}) {
            check(ProviderAccessPolicy.isAllowedDiagnosticValue(key, true), "new typed boolean " + key);
            check(ProviderAccessPolicy.isAllowedDiagnosticValue(key, false), "false retained " + key);
            for (Object value : new Object[]{"true", 1, null, Collections.singletonMap("secret", "value")})
                check(!ProviderAccessPolicy.isAllowedDiagnosticValue(key, value), "mistyped boolean denied " + key);
        }
        check(ProviderAccessPolicy.isAllowedDiagnosticValue("featureState", "native_owned"), "fixed native ownership token accepted");
        for (String state : new String[]{"unavailable", "paused", "waiting_target", "waiting_landscape",
                "waiting_native_config", "connected", "config_submitted", "active_unverified",
                "cleanup_pending", "disabled_unverified", "error", "unsupported_transition", "stale_session"})
            check(!ProviderAccessPolicy.isAllowedDiagnosticValue("featureState", state), "obsolete session state rejected " + state);
        for (String key : new String[]{"driverObserver", "cleanupPending", "sessionActive", "driverSubmitted",
                "driverDisableSubmitted", "nativeListenerDetached", "lastDriverAttempt", "lastDriverAttemptWall",
                "recheckError", "nativeSupport", "profileProbe"}) {
            for (Object value : new Object[]{true, false, 1L, "PRIVATE_CANARY"})
                check(!ProviderAccessPolicy.isAllowedDiagnosticValue(key, value), "obsolete ownership diagnostic rejected " + key);
        }
        for (Object state : new Object[]{"", "PRIVATE_CANARY", "com.example.app", "0:0:com.example.app",
                "active_verified", "native_owned\n", "native_owned:1234", 1, true,
                Collections.singletonMap("state", "connected")})
            check(!ProviderAccessPolicy.isAllowedDiagnosticValue("featureState", state), "identity or unknown state denied");
        check(ProviderAccessPolicy.isAllowedDiagnosticValue("foreground", "0:0:com.example.app"), "bounded diagnostic text");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("foreground", "x".repeat(2001)), "oversize text rejected");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("message", Collections.singletonMap("secret", "value")), "nested data rejected");
        check(ProviderAccessPolicy.isAllowedDiagnosticValue("configRevision", 9L), "integer revision");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("configRevision", 9.5), "fractional revision rejected");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("configRevision", -1), "negative revision rejected");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("receivedElapsed", 1L), "caller cannot set receipt time");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("processUser", 0), "caller cannot set user identity");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("publisherGeneration", 1L), "caller cannot fabricate generation in status");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("assistantGeneration", 1L), "caller cannot fabricate peer generation");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("process", "com.vivo.gamecube:daemon"), "caller cannot override category in JSON");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("configPending", false), "caller cannot override config coherence");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("assistantReady", true), "caller cannot override assistant readiness");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue("anythingElse", "value"), "unknown field rejected");
        check(!ProviderAccessPolicy.isAllowedDiagnosticValue(null, "value"), "missing key rejected");
        System.out.println("PASS: " + checks + " provider boundary/data-minimization assertions (no OEM integration claim)");
    }
}
