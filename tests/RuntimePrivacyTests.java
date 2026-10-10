import ls.iqooaugment.com.core.RuntimeDiagnostics;
import java.util.*;

/** Host policy tests only; no claim about OEM callback or touch-release behavior. */
public final class RuntimePrivacyTests {
    static int checks;
    static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        for (String key : List.of("foreground", "lastAdmission", "lastAdmissionWall", "profileError"))
            check(RuntimeDiagnostics.isActivityDetail(key), "remove disabled " + key);
        for (String key : List.of("ready", "hardware", "admissionHook", "taskObserverAvailable", "master", "bridgeError"))
            check(!RuntimeDiagnostics.isActivityDetail(key), "preserve capability " + key);
        check(RuntimeDiagnostics.acceptsActivity(true, 4, 4), "current enabled diagnostic accepted");
        check(!RuntimeDiagnostics.acceptsActivity(false, 4, 4), "disabled diagnostic rejected");
        check(!RuntimeDiagnostics.acceptsActivity(true, 5, 4), "old diagnostic after off-on rejected");
        check(!RuntimeDiagnostics.acceptsActivity(true, 5, 6), "future diagnostic rejected");
        check(!RuntimeDiagnostics.acceptsActivity(true, -1, -1), "negative diagnostic epoch rejected");
        Map<String, Object> original = new HashMap<>(Map.of("ready", true, "master", false, "receivedElapsed", 1L));
        Map<String, Object> receipt = new HashMap<>(original);
        receipt.put("receivedElapsed", 2L); receipt.put("receivedWall", 100L);
        check(RuntimeDiagnostics.materiallyEqual(original, receipt), "timestamp-only receipt is not a readiness change");
        check(RuntimeDiagnostics.materiallyEqual(receipt, original), "timestamp equality symmetric");
        receipt.put("ready", false);
        check(!RuntimeDiagnostics.materiallyEqual(original, receipt), "readiness change significant");
        receipt.put("ready", true); receipt.put("master", true);
        check(!RuntimeDiagnostics.materiallyEqual(original, receipt), "master change significant");
        receipt.put("master", false); receipt.put("foreground", "0:0:com.example.app");
        check(!RuntimeDiagnostics.materiallyEqual(original, receipt), "new detail significant");
        check(!RuntimeDiagnostics.materiallyEqual(receipt, original), "removed detail significant");
        System.out.println("PASS: " + checks + " runtime privacy policy assertions");
    }
}
