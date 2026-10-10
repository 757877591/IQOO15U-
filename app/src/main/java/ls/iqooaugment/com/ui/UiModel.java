package ls.iqooaugment.com.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import ls.iqooaugment.com.data.Catalog;
import ls.iqooaugment.com.data.Config;
import ls.iqooaugment.com.data.ConfigStore;
import ls.iqooaugment.com.root.RootClient;
import ls.iqooaugment.com.core.ScanPolicy;
import ls.iqooaugment.com.core.UiStatePolicy;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retained across rotation; no Activity is captured by the worker. */
final class UiModel {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService configIo = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private final ConfigStore store;
    private WeakReference<MainActivity> activity = new WeakReference<>(null);
    Config config = Config.empty();
    Catalog catalog;
    String configError = "", scanError = "";
    boolean loading, saving, rootReady, readingApps;
    long scannedAt;
    private boolean pendingScanSelection;
    private Consumer<MainActivity> pendingCompletion;
    private String pendingSaveError;
    private final UiStatePolicy.ConfigReads configReads = new UiStatePolicy.ConfigReads();
    private boolean closed;
    UiModel(Context context) {
        this.context = context.getApplicationContext(); store = new ConfigStore(this.context);
        try { config = store.load(); } catch (Exception e) { configError = e.getMessage(); }
    }
    void attach(MainActivity value) { activity = new WeakReference<>(value); }
    void close() { closed = true; configReads.request(); io.shutdownNow(); configIo.shutdownNow(); }
    void reloadConfig() {
        if (closed) return;
        long request = configReads.request();
        if (saving) return; // Save completion always requests a fresh postcommit read.
        configIo.execute(() -> {
            Config latest = null; String error = "";
            try { latest = store.load(); }
            catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            Config result = latest; String finalError = error;
            main.post(() -> {
                if (!configReads.accepts(request)) return;
                configError = finalError;
                config = result == null ? Config.empty() : result;
                changed();
            });
        });
    }
    void inspect() { read(null, false); }
    void prepareScan() { read(null, true); }
    void scan(Collection<ScanPolicy.SpaceKey> selected) {
        // Freeze the explicit selection before dispatching to the worker.
        Set<ScanPolicy.SpaceKey> scope = new LinkedHashSet<>(selected);
        ScanPolicy.encodeSelection(scope);
        read(scope, false);
    }
    private void read(Set<ScanPolicy.SpaceKey> selected, boolean chooseAfter) {
        if (loading || saving) return;
        loading = true; readingApps = selected != null; rootReady = false; scanError = "";
        catalog = null; // Drop stale app data; the UI separately retains consented draft scope.
        changed();
        io.execute(() -> {
            Catalog result = null; String error = "";
            try {
                RootClient client = new RootClient();
                result = selected == null ? client.inspect(context) : client.scan(context, selected);
            } catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            Catalog finalResult = result; String finalError = error;
            main.post(() -> {
                loading = false; scanError = finalError;
                if (finalResult != null) { catalog = finalResult; rootReady = true; scannedAt = System.currentTimeMillis(); }
                changed();
                if (chooseAfter && finalResult != null) pendingScanSelection = true;
                deliverPending();
            });
        });
    }
    void save(Config value, Consumer<MainActivity> completed) {
        if (saving || !configError.isEmpty()) return;
        configReads.beginSave(); saving = true; changed();
        configIo.execute(() -> {
            String error = "";
            try { store.save(value); } catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            Config latest = null;
            if (!error.isEmpty()) try { latest = store.load(); } catch (Exception ignored) { }
            Config savedLatest = latest;
            String finalError = error;
            main.post(() -> {
                saving = false; configReads.endSave();
                if (finalError.isEmpty()) { config = value; pendingCompletion = completed; }
                else { if (savedLatest != null) config = savedLatest; pendingSaveError = finalError; }
                deliverPending();
                changed();
                reloadConfig(); // Also catches another window's commit after this save.
            });
        });
    }
    void deliverPending() {
        MainActivity current = activity.get();
        if (current == null || !current.canShowDialogs()) return;
        Consumer<MainActivity> completed = pendingCompletion; pendingCompletion = null;
        if (completed != null) completed.accept(current);
        if (pendingSaveError != null) { String error = pendingSaveError; pendingSaveError = null; current.showError("保存失败", error); }
        if (pendingScanSelection) { pendingScanSelection = false; current.showScanSelection(); }
    }
    void changed() { MainActivity view = activity.get(); if (view != null && !view.isFinishing()) view.modelChanged(); }
}
