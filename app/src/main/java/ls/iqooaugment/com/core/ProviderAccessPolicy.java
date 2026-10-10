package ls.iqooaugment.com.core;

/**
 * The supported bridge authenticates the Android system shared UID, not a package
 * or process within that UID. A caller-supplied process name is a status category
 * only. Do not promote it (or a diagnostic PID) into an authentication mechanism.
 */
public final class ProviderAccessPolicy {
    private static final int PER_USER_RANGE = 100000;
    private static final int SYSTEM_APP_ID = 1000;
    private ProviderAccessPolicy() { }

    public static boolean isSystemSharedUid(int uid) {
        return uid >= 0 && uid % PER_USER_RANGE == SYSTEM_APP_ID;
    }

    public static boolean canReadConfig(int callerUid, int ownUid) {
        return callerUid >= 0 && (callerUid == ownUid || isSystemSharedUid(callerUid));
    }

    public static boolean canPublishStatus(int callerUid) {
        return isSystemSharedUid(callerUid);
    }

    /** Validates an untrusted category, without claiming to identify its sender. */
    public static String statusCategory(String reportedProcess) {
        if ("com.vivo.gamecube:daemon".equals(reportedProcess)) return "daemon";
        if ("com.vivo.gamecube:assistantui".equals(reportedProcess)) return "assistantui";
        throw new IllegalArgumentException("Unknown status category");
    }

    /** PID consistency is useful for diagnosis only. Binder UID is the trust boundary. */
    public static boolean matchingDiagnosticPid(int binderPid, Object claimedPid) {
        return binderPid > 0 && (claimedPid instanceof Integer || claimedPid instanceof Long)
            && ((Number) claimedPid).longValue() == binderPid;
    }

    /** The Hook needs only enabled identity tuples, and needs none while paused. */
    public static boolean discloseTargetToHook(boolean master, boolean targetEnabled) {
        return master && targetEnabled;
    }

    /** A delayed report must not be treated as current after disable/re-enable or selection edits. */
    public static boolean matchesCurrentConfig(boolean currentMaster, long currentRevision,
            Object reportedMaster, Object reportedRevision) {
        return currentRevision >= 0 && reportedMaster instanceof Boolean
            && ((Boolean) reportedMaster) == currentMaster
            && (reportedRevision instanceof Integer || reportedRevision instanceof Long)
            && ((Number) reportedRevision).longValue() == currentRevision;
    }

    public static boolean mayRetainActivity(boolean currentMaster, long currentRevision,
            Object reportedMaster, Object reportedRevision) {
        return currentMaster && matchesCurrentConfig(currentMaster, currentRevision,
            reportedMaster, reportedRevision);
    }

    /** Drop unknown, nested and mistyped diagnostics instead of retaining arbitrary payloads. */
    public static boolean isAllowedDiagnosticValue(String key, Object value) {
        if (key == null || value == null) return false;
        switch (key) {
            case "ready": case "hardware": case "taskObserver": case "admissionHook":
            case "master": case "taskObserverAvailable": case "fullFeatureHooks":
                return value instanceof Boolean;
            case "featureState":
                return value instanceof String && isFeatureState((String) value);
            case "version": case "message": case "bridgeError": case "observerError":
            case "foreground": case "profileError": case "lastAdmission":
                return value instanceof String && ((String) value).length() <= 2000;
            case "configRevision": case "lastAdmissionWall":
                return (value instanceof Integer || value instanceof Long)
                    && ((Number) value).longValue() >= 0;
            default:
                return false;
        }
    }

    /** A fixed ownership token contains no app, user, profile, coordinate or error identity. */
    private static boolean isFeatureState(String value) {
        return "native_owned".equals(value);
    }
}
