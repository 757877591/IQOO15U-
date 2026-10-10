package ls.iqooaugment.com.data;

import android.content.Context;
import ls.iqooaugment.com.core.RuntimeReadiness;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

/** In-memory diagnostic snapshots tied to live publisher identities, never a timed lease. */
public final class RuntimeStatus {
    public final JSONObject daemon, assistant;
    /** Kept for UI/API compatibility: fresh means live Binder and matching publisher generation. */
    public final boolean daemonFresh, assistantFresh;
    private final Config config;
    public RuntimeStatus(Context context) {
        synchronized (ConfigStore.STATUS_LOCK) {
            Config current;
            try { current = new ConfigStore(context).load(); }
            catch (Exception unavailable) { current = null; }
            config = current;
            daemon = read(context, "daemon");
            assistant = read(context, "assistantui");
            daemonFresh = config != null && ConfigProvider.liveStatus(daemon);
            assistantFresh = config != null && ConfigProvider.liveStatus(assistant);
        }
    }
    private static JSONObject read(Context context, String key) {
        try { return new JSONObject(ConfigStore.readStatus(context, key)); }
        catch (Exception unavailable) { return new JSONObject(); }
    }
    public boolean ready() {
        return config != null && RuntimeReadiness.ready(daemonFresh, assistantFresh,
            config.master, config.revision, fields(daemon), fields(assistant));
    }
    /** Caller reads the same-user assistant snapshot and config under STATUS_LOCK. */
    static boolean assistantReady(Context context, Config current, JSONObject state) {
        return RuntimeReadiness.assistantReady(ConfigProvider.liveStatus(state),
            current.master, current.revision, fields(state));
    }
    private static Map<String, Object> fields(JSONObject state) {
        Map<String, Object> result = new HashMap<>();
        Iterator<String> keys = state.keys();
        while (keys.hasNext()) { String key = keys.next(); result.put(key, state.opt(key)); }
        return result;
    }
    public String message() {
        if (config == null) return "配置无法读取；已暂停扩展";
        if (!daemonFresh) return "未收到有效 Hook 确认：请启用模块、勾选游戏魔盒并重启";
        if (!assistantFresh) return "未收到有效肩键界面 Hook 确认：请打开原生游戏侧边栏后重试";
        if (!daemon.optString("bridgeError").isEmpty() || !assistant.optString("bridgeError").isEmpty())
            return "配置桥接失败；已暂停扩展";
        if (!RuntimeReadiness.currentConfig(config.master, config.revision, fields(daemon))
                || !RuntimeReadiness.currentConfig(config.master, config.revision, fields(assistant)))
            return "配置已保存；正在等待守护进程和肩键界面应用同一配置";
        if (!daemon.optBoolean("admissionHook") || !assistant.optBoolean("fullFeatureHooks"))
            return "应用资格 Hook 尚未完整接入；已暂停扩展";
        if (!daemon.optBoolean("hardware") || !assistant.optBoolean("hardware"))
            return "肩键硬件能力尚未确认；已暂停扩展";
        if (config.master && !assistant.optBoolean("taskObserver"))
            return "应用资格观察尚未就绪；已暂停追加资格，原生映射和操作保持系统处理";
        if (!ready()) return "应用资格 Hook 尚未就绪；原生映射和触摸效果未验证";
        if (!daemon.optString("profileError").isEmpty() || !assistant.optString("profileError").isEmpty())
            return "Hook 已接入，但最近一次空间识别失败";
        if (!config.master) return "应用资格 Hook 已接入 · 总开关关闭";
        return "仅扩展所选实例的肩键资格 · 映射菜单与操作由系统原生功能提供 · 真机效果未验证";
    }
}
