package ls.iqooaugment.com.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.RuntimeDiagnostics;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

/** Only user selections persist. Runtime activity diagnostics are process-local and never backed up. */
public final class ConfigStore {
    /** Serializes disabling and publishing, so an in-flight heartbeat cannot restore private details. */
    public static final Object STATUS_LOCK = new Object();
    private static final Map<String, String> LIVE_STATUS = new HashMap<>();
    private static boolean legacyStatusPurged;
    private static String cachedRaw;
    private static Config cachedConfig;
    private final Context context;
    private final SharedPreferences prefs;
    public ConfigStore(Context context) {
        this.context = context.createDeviceProtectedStorageContext();
        prefs = this.context.getSharedPreferences("selection", Context.MODE_PRIVATE);
        synchronized (STATUS_LOCK) { purgeLegacyStatusLocked(this.context); }
    }
    public Config load() throws IOException {
        synchronized (STATUS_LOCK) {
            String raw = prefs.getString("config", null);
            if (raw == null) return Config.empty();
            if (cachedConfig != null && raw.equals(cachedRaw)) return cachedConfig;
            try {
                Config parsed = Config.parse(raw);
                cachedRaw = raw; cachedConfig = parsed; return parsed;
            } catch (Exception error) {
                cachedRaw = null; cachedConfig = null;
                throw new IOException("选择数据无法读取；已停止扩展，未覆盖原数据。", error);
            }
        }
    }
    public void save(Config config) throws IOException {
        try {
            boolean changed;
            synchronized (STATUS_LOCK) {
                String encoded = config.encode();
                String previousRaw = prefs.getString("config", null);
                changed = !encoded.equals(previousRaw);
                Config previous = load();
                if (changed && (previous.revision >= Long.MAX_VALUE - 1 || config.revision != previous.revision + 1))
                    throw new IOException("配置已在其他窗口更新，请查看最新选择后重试。");
                if (changed && !prefs.edit().putString("config", encoded).commit()) throw new IOException("配置保存失败");
                cachedRaw = encoded; cachedConfig = config;
                if (!config.master) clearActivityStatus(context);
                if (changed) markConfigPendingLocked(config);
            }
            if (changed) {
                context.getContentResolver().notifyChange(Uri.parse("content://" + Constants.AUTHORITY + "/config"), null);
                context.getContentResolver().notifyChange(Uri.parse("content://" + Constants.AUTHORITY + "/status"), null);
            }
        } catch (org.json.JSONException e) { throw new IOException("配置编码失败", e); }
    }
    private static void markConfigPendingLocked(Config config) {
        // Keep no fabricated ready snapshot when the provider has not heard from a Hook yet.
        for (Map.Entry<String, String> item : LIVE_STATUS.entrySet()) {
            try {
                JSONObject value = redactActivityStatus(new JSONObject(item.getValue()));
                value.put("master", config.master); value.put("configPending", true);
                item.setValue(value.toString());
            } catch (Exception ignored) { item.setValue("{}"); }
        }
    }
    static void removeStatusLocked(String key) {
        if (!Thread.holdsLock(STATUS_LOCK)) throw new IllegalStateException("Status lock required");
        LIVE_STATUS.remove(key);
    }
    public static String readStatus(Context context, String key) {
        synchronized (STATUS_LOCK) {
            purgeLegacyStatusLocked(context);
            return LIVE_STATUS.getOrDefault(key, "{}");
        }
    }
    /** Caller holds STATUS_LOCK and has already authenticated/canonicalized the snapshot. */
    public static boolean publishStatusLocked(Context context, String key, JSONObject value) {
        if (!Thread.holdsLock(STATUS_LOCK)) throw new IllegalStateException("Status lock required");
        purgeLegacyStatusLocked(context);
        String previous = LIVE_STATUS.put(key, value.toString());
        if (previous == null) return true;
        try { return !RuntimeDiagnostics.materiallyEqual(asMap(new JSONObject(previous)), asMap(value)); }
        catch (Exception ignored) { return true; }
    }
    public static void clearActivityStatus(Context context) {
        synchronized (STATUS_LOCK) {
            purgeLegacyStatusLocked(context);
            for (Map.Entry<String, String> item : LIVE_STATUS.entrySet()) {
                try {
                    JSONObject value = new JSONObject(item.getValue());
                    value = redactActivityStatus(value); value.put("master", false);
                    item.setValue(value.toString());
                } catch (Exception ignored) { item.setValue("{}"); }
            }
        }
    }
    public static JSONObject redactActivityStatus(JSONObject value) throws org.json.JSONException {
        JSONObject clean = new JSONObject(value.toString());
        Iterator<String> keys = clean.keys();
        while (keys.hasNext()) if (RuntimeDiagnostics.isActivityDetail(keys.next())) keys.remove();
        return clean;
    }
    private static Map<String, Object> asMap(JSONObject value) {
        Map<String, Object> result = new HashMap<>();
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) { String key = keys.next(); result.put(key, value.opt(key)); }
        return result;
    }
    private static void purgeLegacyStatusLocked(Context context) {
        if (legacyStatusPurged) return;
        SharedPreferences legacy = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("runtime_status", Context.MODE_PRIVATE);
        if (!legacy.getAll().isEmpty() && !legacy.edit().clear().commit())
            throw new IllegalStateException("旧运行诊断清理失败；已停止状态记录");
        legacyStatusPurged = true;
    }
}
