package ls.iqooaugment.com.core;

import java.util.Map;

/** Live, coherent eligibility hooks are not proof of native mapping or physical shoulder output. */
public final class RuntimeReadiness {
    private RuntimeReadiness() { }

    public static boolean currentConfig(boolean master, long revision, Map<String, ?> status) {
        return status != null && Boolean.FALSE.equals(status.get("configPending"))
            && ProviderAccessPolicy.matchesCurrentConfig(master, revision,
                status.get("master"), status.get("configRevision"));
    }

    public static boolean daemonReady(boolean live, boolean master, long revision, Map<String, ?> status) {
        return commonReady(live, master, revision, status)
            && Boolean.TRUE.equals(status.get("admissionHook"));
    }

    /** Independent of daemon readiness so that the two processes initialize in either order. */
    public static boolean assistantReady(boolean live, boolean master, long revision, Map<String, ?> status) {
        return commonReady(live, master, revision, status)
            && Boolean.TRUE.equals(status.get("fullFeatureHooks"))
            && (!master || Boolean.TRUE.equals(status.get("taskObserver")));
    }

    public static boolean ready(boolean daemonLive, boolean assistantLive, boolean master,
            long revision, Map<String, ?> daemon, Map<String, ?> assistant) {
        return daemonReady(daemonLive, master, revision, daemon)
            && assistantReady(assistantLive, master, revision, assistant);
    }

    private static boolean commonReady(boolean live, boolean master, long revision, Map<String, ?> status) {
        return live && currentConfig(master, revision, status)
            && Boolean.TRUE.equals(status.get("ready"))
            && Boolean.TRUE.equals(status.get("hardware"))
            && emptyError(status.get("bridgeError"));
    }

    private static boolean emptyError(Object value) {
        return value == null || (value instanceof String && ((String) value).isEmpty());
    }
}
