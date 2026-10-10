package ls.iqooaugment.com.data;

import ls.iqooaugment.com.core.DiagnosticPolicy;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Deliberately has no API for exporting the raw config or runtime snapshots. */
public final class Diagnostics {
    private Diagnostics() { }
    public static JSONObject summary(String moduleVersion, int moduleCode, int sdk,
            Config config, boolean rootChecked, RuntimeStatus runtime) throws JSONException {
        return new JSONObject().put("format", "redacted-summary-v2")
            .put("module", moduleVersion).put("moduleCode", moduleCode).put("sdk", sdk)
            .put("master", config.master).put("selectedCount", config.targets.size())
            .put("enabledCount", config.enabled.size()).put("rootCheckedThisSession", rootChecked)
            .put("statusBasis", "live-binder-and-publisher-generation")
            .put("daemonFresh", runtime.daemonFresh).put("assistantFresh", runtime.assistantFresh)
            .put("daemon", safeStatus(runtime.daemon)).put("assistantui", safeStatus(runtime.assistant));
    }
    private static JSONObject safeStatus(JSONObject status) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Iterator<String> keys = status.keys();
        while (keys.hasNext()) { String key = keys.next(); fields.put(key, status.opt(key)); }
        return new JSONObject(DiagnosticPolicy.summarize(fields));
    }
}
