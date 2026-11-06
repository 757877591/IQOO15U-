package ls.iqooaugment.com.hook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.InputEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import ls.iqooaugment.com.core.NativeCategoryScope;
import ls.iqooaugment.com.core.GameCubeVersion;

/** Version-selected native eligibility/category adapter. All shoulder operations remain OEM-owned. */
final class AssistantHooks {
    private static final String BASE = "com.vivo.gameassistant.inputbuttons.gameshoulder.";
    private static final String K = BASE + "utils.k";
    private static final String DETAILS = BASE + "entity.GameShoulderDetailsEntity";
    private static final String ITEM = "com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.model.j";
    private static final String ITEM_TYPE = "com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.model.QuickSwitchItemType";
    private static final String FRAME = "iqoo.native.category";
    private final Context context;
    private final ClassLoader loader;
    private final BridgeClient bridge;
    private final GameCubeVersion version;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TaskWatcher watcher = new TaskWatcher();
    private final SelectedEligibility eligibility;
    private final NativeCategoryScope category = new NativeCategoryScope();
    private final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private volatile boolean installed;
    private volatile SelectedEligibility.Selection genericCached;
    private Method nativeIsWang;
    private final Runnable foregroundChanged;

    AssistantHooks(Context context, ClassLoader loader, BridgeClient bridge, GameCubeVersion version) {
        this.context = context; this.loader = loader; this.bridge = bridge; this.version = version;
        foregroundChanged = () -> refreshGeneric(null, this.context);
        eligibility = new SelectedEligibility(context, bridge,
            () -> installed && watcher.isRunning());
    }
    void install() throws Exception {
        try {
            validateContract();
            installCapability();
            installCategoryScopes();
            installForegroundObservation();
            watcher.prepare(context, () -> {
                // Invalidate synchronously on the event thread. Never carry an old profile to a queued refresh.
                invalidate();
                main.removeCallbacks(foregroundChanged); main.post(foregroundChanged);
            });
            bridge.onConfigChanged(this::configurationChanged);
            installed = true;
            bridge.set("fullFeatureHooks", true);
            bridge.set("taskObserverAvailable", true);
            bridge.set("taskObserver", false);
            bridge.set("featureState", "native_owned");
        } catch (Throwable error) {
            installed = false; invalidate();
            for (XC_MethodHook.Unhook hook : hooks) try { hook.unhook(); } catch (Throwable ignored) { }
            hooks.clear();
            bridge.set("fullFeatureHooks", false);
            throw new IllegalStateException("完整原生肩键分类接口不匹配；未启用追加功能", error);
        }
    }
    private Class<?> type(String name) { return XposedHelpers.findClass(name, loader); }
    private Method method(String owner, String name, Class<?> result, Class<?>... args) throws Exception {
        Method value = type(owner).getDeclaredMethod(name, args);
        if (value.getReturnType() != result) throw new NoSuchMethodException(owner + "." + name);
        value.setAccessible(true); return value;
    }
    private void hook(Method method, XC_MethodHook callback) { hooks.add(XposedBridge.hookMethod(method, callback)); }
    private void field(String owner, String name, Class<?> expected) throws Exception {
        if (type(owner).getDeclaredField(name).getType() != expected) throw new NoSuchFieldException(owner + "." + name);
    }
    private void validateContract() throws Exception {
        nativeIsWang = method(version.gameUtils, version.specialGame, boolean.class, String.class);
        field(version.presenter, "d", String.class); field(version.presenter, "c", Context.class);
        field(version.viewManager, "b", type(BASE + "IGameShoulderContract$IPresenter"));
        field(BASE + "view.GameShoulderSettingsView", version.settingsPresenter, type(version.viewManager));
        field(BASE + "view.GameShoulderSettingsView", version.settingsPackage, String.class);
        field(BASE + "view.GameShoulderPreferencesView", version.preferencesParent, type(BASE + "IGameShoulderContract$IMainView"));
        field(BASE + "view.GameShoulderUserGuideView", "A", type(BASE + "IGameShoulderContract$IMainView"));
        method("com.vivo.common.a", version.capabilityInstance, type("com.vivo.common.a"));
        method("com.vivo.common.a", version.shoulderHardware, boolean.class);
        method(version.assistantManager, "V", type(version.assistantManager));
        method(version.assistantManager, version.foregroundPackage, String.class);
        method(ITEM, version.itemType, type(ITEM_TYPE));
    }
    private void invalidate() { eligibility.invalidate(); genericCached = null; }
    private void configurationChanged() {
        invalidate(); main.removeCallbacks(foregroundChanged);
        try {
            if (installed && bridge.current().master) watcher.start(); else watcher.stop();
            bridge.set("observerError", "");
        } catch (Throwable error) { bridge.set("observerError", error.getClass().getSimpleName()); }
        bridge.set("taskObserver", watcher.isRunning());
        if (installed && bridge.current().master && watcher.isRunning()) main.post(foregroundChanged);
        // Existing OEM output is not forcibly closed or rewritten when module selection changes.
    }
    private boolean hardware() {
        try { return Boolean.TRUE.equals(XposedHelpers.callMethod(
            XposedHelpers.callStaticMethod(type("com.vivo.common.a"), version.capabilityInstance), version.shoulderHardware)); }
        catch (Throwable ignored) { return false; }
    }
    private boolean genericSafe(SelectedEligibility.Selection selected, Context nativeContext) {
        if (!eligibility.valid(selected)) return false;
        try {
            boolean special = Boolean.TRUE.equals(XposedBridge.invokeOriginalMethod(nativeIsWang, null,
                new Object[]{selected.key.packageName}));
            if (!special) return true;
            // Eligibility only: never reset a running SDK mapping. Native close owns its reset.
            int nativeUser = ((Number) XposedHelpers.callMethod(nativeContext, "getUserId")).intValue();
            return nativeUser == selected.key.userId
                && Settings.Secure.getInt(nativeContext.getContentResolver(), "game_shoulder_mapping_type_left", -1) == 0
                && Settings.Secure.getInt(nativeContext.getContentResolver(), "game_shoulder_mapping_type_right", -1) == 0;
        } catch (Throwable ignored) { return false; }
    }
    private SelectedEligibility.Selection refreshGeneric(String pkg, Context nativeContext) {
        if (category.cacheOnly()) return pkg == null ? null : cachedGeneric(pkg);
        SelectedEligibility.Selection selected = eligibility.resolve(pkg);
        if (selected == null || !genericSafe(selected, nativeContext) || !eligibility.valid(selected)) {
            genericCached = null; return null;
        }
        genericCached = selected; return selected;
    }
    private SelectedEligibility.Selection cachedGeneric(String pkg) {
        SelectedEligibility.Selection selected = genericCached;
        return eligibility.valid(selected) && selected.key.packageName.equals(pkg) ? selected : null;
    }
    private void installCapability() throws Exception {
        for (String name : new String[]{"p", "q"}) hook(method(K, name, boolean.class, String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                // Native SettingsView.t1 calls p during a press. Nested predicates inherit cache-only mode.
                boolean localOnly = category.cacheOnly();
                SelectedEligibility.Selection selected = localOnly
                    ? cachedGeneric((String) param.args[0]) : refreshGeneric((String) param.args[0], context);
                // p is just an app-category exclusion. q retains its original hardware result in a hot scope.
                boolean supported = localOnly ? name.equals("p") || Boolean.TRUE.equals(param.getResult()) : hardware();
                if (selected != null && supported && eligibility.valid(selected)) param.setResult(true);
            }
        });
        hook(method(ITEM, "C", void.class, int.class, float.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (XposedHelpers.callMethod(param.thisObject, version.itemType) != XposedHelpers.getStaticObjectField(type(ITEM_TYPE), "GAME_SHOULDER")) return;
                    String pkg = nativePackage();
                    if (refreshGeneric(pkg, context) != null) param.args[0] = 1;
                } catch (Throwable ignored) { }
            }
        });
    }
    private String nativePackage() {
        return (String) XposedHelpers.callMethod(XposedHelpers.callStaticMethod(type(version.assistantManager), "V"), version.foregroundPackage);
    }
    private void installForegroundObservation() throws Exception {
        hook(method(version.presenter, "handleGameForeground", void.class, boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                invalidate();
                if (Boolean.TRUE.equals(param.args[0])) {
                    bridge.onLifecycleEvent();
                    try { refreshGeneric(nativePackage(), (Context) XposedHelpers.getObjectField(param.thisObject, "c")); }
                    catch (Throwable ignored) { }
                }
                // Original always executes, including false -> close/unregister/reset. No replay.
            }
        });
    }
    private Object presenter(Object receiver, String path) {
        Object value = receiver;
        if (!path.isEmpty()) for (String name : path.split("\\.")) value = XposedHelpers.getObjectField(value, name);
        return type(version.presenter).isInstance(value) ? value : null;
    }
    private void allow(String owner, String name, String path, boolean inputOnly, Class<?>... args) throws Exception {
        hook(method(owner, name, void.class, args), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                SelectedEligibility.Selection selected = null;
                try {
                    Object nativePresenter = presenter(param.thisObject, path);
                    if (nativePresenter != null) {
                        String pkg = (String) XposedHelpers.getObjectField(nativePresenter, "d");
                        boolean matchesView = !owner.endsWith("GameShoulderSettingsView")
                            || pkg != null && pkg.equals(XposedHelpers.getObjectField(param.thisObject, version.settingsPackage));
                        if (matchesView) selected = inputOnly || category.cacheOnly() ? cachedGeneric(pkg) : refreshGeneric(pkg,
                            (Context) XposedHelpers.getObjectField(nativePresenter, "c"));
                    }
                } catch (Throwable ignored) { }
                param.setObjectExtra(FRAME, category.enter(selected == null ? null : selected.key, selected, inputOnly));
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                category.leave((NativeCategoryScope.Frame) param.getObjectExtra(FRAME));
            }
        });
    }
    private void barrier(Method method) {
        hook(method, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.setObjectExtra(FRAME, category.enter(null, null));
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                category.leave((NativeCategoryScope.Frame) param.getObjectExtra(FRAME));
            }
        });
    }
    private void installCategoryScopes() throws Exception {
        // Exact single-purpose category callers. OEM operation arguments/results remain unchanged.
        String settingsPath = version.settingsPresenter + ".b";
        allow(version.presenter, version.driverCategory, "", false, int.class, boolean.class);
        allow(version.presenter, version.sdkCategory, "", false, type(DETAILS));
        allow(BASE + "view.GameShoulderSettingsView", version.settingsInit, settingsPath, false);
        allow(BASE + "view.GameShoulderSettingsView", version.settingsScheme, settingsPath, false);
        allow(BASE + "view.GameShoulderPreferencesView", version.preferencesInit, version.preferencesParent + "." + settingsPath, false);
        allow(BASE + "view.GameShoulderUserGuideView", version.guideInit, "A." + settingsPath, false);
        // Two native visual category checks. Cached identity only: no foreground/Settings/Provider IPC per press.
        allow(version.presenter, version.inputCategory, "", true, InputEvent.class, int.class);
        allow(version.viewManager, "processPressAnim", "b", true, InputEvent.class, boolean.class, int.class, boolean.class, boolean.class, int.class);
        // All other direct category callers in each audited DEX retain their original classification.
        barrier(method(K, "q", boolean.class, String.class));
        barrier(method(BASE + "utils.l", "a", java.util.HashMap.class, Context.class, boolean.class));
        barrier(method(version.gameUtils, version.reviewCategory, boolean.class, Context.class));
        List<Class<?>> switchArgs = new ArrayList<>(java.util.Arrays.asList(Context.class,
            type("android.multidisplay.MultiDisplayManager"), type(version.switchHelper),
            boolean.class, boolean.class, List.class, List.class));
        if (version == GameCubeVersion.V15) switchArgs.add(type("gi.b"));
        barrier(method(version.switchBuilder, "h", void.class, switchArgs.toArray(new Class<?>[0])));
        barrier(method(version.interpolation, version.interpolationCategory, void.class, int.class, int.class, int.class, int.class,
            type("com.vivo.gameassistant.frameinterpolation.FrameInterpolationState")));
        barrier(method(BASE + "a", "onGameSdkEvent", void.class, type("com.vivo.gameassistant.entity.GameSDKEvent")));
        hook(nativeIsWang, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !Boolean.TRUE.equals(param.getResult())) return;
                NativeCategoryScope.Frame frame = category.current();
                if (frame == null || !(frame.evidence instanceof SelectedEligibility.Selection)) return;
                SelectedEligibility.Selection selected = (SelectedEligibility.Selection) frame.evidence;
                if (eligibility.valid(selected) && category.allows((String) param.args[0], selected)) param.setResult(false);
            }
        });
    }
}
