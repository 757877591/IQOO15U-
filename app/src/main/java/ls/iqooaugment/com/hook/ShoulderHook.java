package ls.iqooaugment.com.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.AdmissionPolicy;
import ls.iqooaugment.com.core.GameCubeVersion;
import ls.iqooaugment.com.data.Config;

/** Extends native admission only; never invokes or owns the native foreground lifecycle. */
public final class ShoulderHook implements IXposedHookLoadPackage {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicInteger LOG_LINES = new AtomicInteger();
    private final ThreadLocal<SelectedEligibility.Selection> frame = new ThreadLocal<>();
    private BridgeClient bridge;
    private SelectedEligibility eligibility;
    private volatile boolean ready;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!Constants.CUBE.equals(lp.packageName)
            || !(lp.processName.equals(Constants.CUBE + ":daemon") || lp.processName.equals(Constants.CUBE + ":assistantui"))) return;
        try {
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.args[0] instanceof Application)) return;
                    Context context = (Application) param.args[0];
                    if (!Constants.CUBE.equals(context.getPackageName()) || !INSTALLED.compareAndSet(false, true)) return;
                    initialize(context, lp.processName);
                }
            });
        } catch (Throwable error) { log("Application initialization hook failed: " + error.getClass().getSimpleName()); }
    }
    private void initialize(Context context, String process) {
        bridge = new BridgeClient(context, process);
        eligibility = new SelectedEligibility(context, bridge, () -> ready);
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(Constants.CUBE, 0);
            bridge.set("version", pi.versionName + "/" + pi.getLongVersionCode());
            GameCubeVersion version = GameCubeVersion.find(pi.versionName, pi.getLongVersionCode());
            if (version == null)
                throw new IllegalStateException("游戏魔盒版本不匹配；未安装扩展 Hook");
            ClassLoader cl = context.getClassLoader();
            Object cap = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.vivo.common.a", cl), version.capabilityInstance);
            boolean hardware = Boolean.TRUE.equals(XposedHelpers.callMethod(cap, version.shoulderHardware));
            bridge.set("hardware", hardware);
            if (!hardware) throw new IllegalStateException("系统未报告原生肩键硬件能力");
            if (process.endsWith(":daemon")) {
                if (version == GameCubeVersion.V15) installDaemon15(cl); else installDaemon(cl);
                bridge.onConfigChanged(eligibility::invalidate);
            } else new AssistantHooks(context, cl, bridge, version).install();
            ready = true;
            bridge.set("ready", true);
            bridge.set("message", "原厂菜单准入已安装；输入与关闭由原厂处理，未做真机验证");
            log("Ready " + process);
        } catch (Throwable error) {
            ready = false; eligibility.invalidate();
            bridge.set("ready", false); bridge.set("message", error.toString());
            log("Disabled " + process + ": " + error.getClass().getSimpleName());
        } finally { bridge.start(); }
    }
    private void installDaemon15(ClassLoader cl) throws Exception {
        Class<?> daemon = XposedHelpers.findClass("com.vivo.gamedaemon.h", cl);
        // 1500701 passes the native support list into onTopActivityChange instead of reading it there.
        Method top = daemon.getDeclaredMethod("v0", List.class, boolean.class, int.class, String.class, boolean.class);
        if (top.getReturnType() != void.class || daemon.getDeclaredField("b").getType() != String.class)
            throw new IllegalStateException("h.v0/b signature mismatch");
        XposedBridge.hookMethod(top, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                bridge.onLifecycleEvent();
                if (!ready || !bridge.current().master || !bridge.assistantReady()) return;
                try {
                    String pkg = (String) XposedHelpers.getObjectField(param.thisObject, "b");
                    if (pkg == null || pkg.isEmpty() || !(param.args[0] instanceof List<?>)) return;
                    SelectedEligibility.Selection selected = eligibility.resolve(pkg);
                    if (!eligibility.valid(selected) || !bridge.assistantReady()) return;
                    List<?> raw = (List<?>) param.args[0];
                    for (Object item : raw) if (!(item instanceof String)) return;
                    @SuppressWarnings("unchecked") List<String> original = (List<String>) raw;
                    Config config = selected.config;
                    List<String> result = AdmissionPolicy.extend(original, selected.key, config.enabled, config.master, true);
                    if (result != original && eligibility.valid(selected)) param.args[0] = result;
                    // Only this invocation receives a copy. Original flags, old package/PID and lifecycle execute intact.
                } catch (Throwable ignored) { }
            }
        });
        bridge.set("admissionHook", true);
    }
    private void installDaemon(ClassLoader cl) throws Exception {
        Class<?> listClass = XposedHelpers.findClass("com.vivo.gamedaemon.i0", cl);
        Method listMethod = listClass.getDeclaredMethod("A");
        if (!List.class.isAssignableFrom(listMethod.getReturnType())) throw new IllegalStateException("i0.A signature mismatch");
        Class<?> daemon = XposedHelpers.findClass("com.vivo.gamedaemon.g", cl);
        Method top = daemon.getDeclaredMethod("t0", int.class, String.class);
        if (top.getReturnType() != void.class || daemon.getDeclaredField("b").getType() != String.class)
            throw new IllegalStateException("g.t0/b signature mismatch");
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            hooks.add(XposedBridge.hookMethod(listMethod, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.getResult() instanceof List<?>)) return;
                    SelectedEligibility.Selection selected = frame.get();
                    if (!ready || !eligibility.valid(selected) || !bridge.assistantReady()) return;
                    List<?> raw = (List<?>) param.getResult();
                    for (Object item : raw) if (!(item instanceof String)) return;
                    @SuppressWarnings("unchecked") List<String> original = (List<String>) raw;
                    Config config = selected.config;
                    List<String> result = AdmissionPolicy.extend(original, selected.key, config.enabled, config.master, true);
                    if (result != original) param.setResult(result);
                }
            }));
            hooks.add(XposedBridge.hookMethod(top, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    param.setObjectExtra("iqoo.admission.previous", frame.get()); frame.remove();
                    // This is an actual OEM lifecycle entry. Its first integer is never treated as a userId.
                    bridge.onLifecycleEvent();
                    if (!ready || !bridge.current().master || !bridge.assistantReady()) return;
                    try { frame.set(eligibility.resolve((String) XposedHelpers.getObjectField(param.thisObject, "b"))); }
                    catch (Throwable ignored) { frame.remove(); }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    SelectedEligibility.Selection previous = (SelectedEligibility.Selection) param.getObjectExtra("iqoo.admission.previous");
                    if (previous == null) frame.remove(); else frame.set(previous);
                }
            }));
            bridge.set("admissionHook", true);
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) try { hook.unhook(); } catch (Throwable ignored) { }
            throw new IllegalStateException("原生准入接口不匹配", error);
        }
    }
    static void log(String text) {
        int line = LOG_LINES.getAndUpdate(value -> value < 17 ? value + 1 : 17);
        if (line < 16) XposedBridge.log("[IQOO肩键扩展] " + text);
        else if (line == 16) XposedBridge.log("[IQOO肩键扩展] 本进程日志已达上限；后续状态仅保留内存摘要");
    }
}
