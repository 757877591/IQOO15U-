package ls.iqooaugment.com.core;

import java.util.Map;
import java.util.Objects;

/** Pure privacy/status policy shared by the bridge and app process. */
public final class RuntimeDiagnostics {
    private RuntimeDiagnostics() { }
    public static boolean isActivityDetail(String key) {
        return "foreground".equals(key) || "lastAdmission".equals(key)
            || "lastAdmissionWall".equals(key) || "profileError".equals(key);
    }
    public static boolean isReceiptTimestamp(String key) {
        return "receivedElapsed".equals(key)
            || "receivedWall".equals(key);
    }
    public static boolean materiallyEqual(Map<String, ?> left, Map<String, ?> right) {
        for (String key : left.keySet()) if (!isReceiptTimestamp(key)
                && (!right.containsKey(key) || !Objects.equals(left.get(key), right.get(key)))) return false;
        for (String key : right.keySet()) if (!isReceiptTimestamp(key) && !left.containsKey(key)) return false;
        return true;
    }
    public static boolean acceptsActivity(boolean master, long currentRevision, long evaluationRevision) {
        return master && currentRevision >= 0 && currentRevision == evaluationRevision;
    }
}
