package ls.iqooaugment.com.hook;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.UserManager;
import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.data.Config;
import ls.iqooaugment.com.core.RuntimeDiagnostics;
import ls.iqooaugment.com.core.RefreshGate;
import ls.iqooaugment.com.root.AndroidUsers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

/** Event-only configuration cache. No timer, cache read or native input renews a lease. */
final class BridgeClient {
    final Context context;
    final String process;
    final Handler worker;
    private final Map<String, Object> status = new LinkedHashMap<>();
    private final IBinder publisher = new Binder();
    private final Object connectionLock = new Object();
    private static final class Snapshot {
        final Config config;
        final IBinder provider, assistant;
        final long generation, assistantGeneration;
        final boolean assistantReady;
        Snapshot(Config config, IBinder provider, long generation, IBinder assistant,
                long assistantGeneration, boolean assistantReady) {
            this.config = config; this.provider = provider; this.generation = generation;
            this.assistant = assistant; this.assistantGeneration = assistantGeneration;
            this.assistantReady = assistantReady;
        }
        static Snapshot empty() { return new Snapshot(Config.empty(), null, 0, null, 0, false); }
        Snapshot withoutReadiness() {
            return new Snapshot(config, provider, generation, null, 0, false);
        }
    }
    private volatile Snapshot snapshot = Snapshot.empty();
    private IBinder provider, assistant;
    private long generation, assistantGeneration;
    private IBinder.DeathRecipient providerDeath, assistantDeath;
    private final RefreshGate.Epoch invalidation = new RefreshGate.Epoch();
    private Uri root;
    private ContentObserver configObserver, statusObserver;
    private boolean observersRegistered, started, dirty = true;
    private final RefreshGate refreshGate = new RefreshGate();
    private volatile Runnable changed = () -> { };
    private final Handler control = new Handler(Looper.getMainLooper());
    private final Runnable controlChanged = () -> changed.run();
    private void notifyChanged() { control.removeCallbacks(controlChanged); control.post(controlChanged); }
    private final Runnable push = this::pushStatus;
    private final Runnable refresh = this::refresh;

    BridgeClient(Context context, String process) {
        this.context = context; this.process = process;
        HandlerThread thread = new HandlerThread("IQOO-config"); thread.start(); worker = new Handler(thread.getLooper());
        status.put("pid", Process.myPid()); status.put("process", process);
        status.put("processUser", Process.myUid()/100000);
        status.put("ready", false); status.put("master", false); status.put("message", "正在初始化");
    }
    void onConfigChanged(Runnable callback) { changed = callback; }
    void start() { worker.post(() -> { started = true; requestRefresh(); }); }
    /** Read-only: even repeated input/cache reads cannot issue IPC or schedule any work. */
    Config current() {
        Snapshot cached = snapshot;
        return cached.provider != null && cached.provider.isBinderAlive() ? cached.config : Config.empty();
    }
    boolean assistantReady() {
        Snapshot cached = snapshot;
        return cached.config.master && cached.provider != null && cached.provider.isBinderAlive()
            && cached.assistantReady && cached.assistantGeneration > 0
            && cached.assistant != null && cached.assistant.isBinderAlive();
    }
    /** Only initialization, configuration and explicit readiness events call this. */
    private void requestRefresh() {
        synchronized (connectionLock) {
            if (refreshGate.request()) worker.post(refresh);
        }
    }
    /** A real OEM lifecycle entry may recover a disconnected bridge; healthy cache stays local. */
    void onLifecycleEvent() {
        synchronized (connectionLock) {
            Snapshot cached = snapshot;
            if (cached.provider == null || !cached.provider.isBinderAlive()
                    || (process.endsWith(":daemon") && (assistant == null || !assistant.isBinderAlive())))
                requestRefresh();
        }
    }
    void set(String key, Object value) { put(key, value, -1); }
    void setActivity(String key, Object value, long evaluationRevision) { put(key, value, evaluationRevision); }
    private void put(String key, Object value, long evaluationRevision) {
        synchronized (status) {
            Config now = current();
            if (RuntimeDiagnostics.isActivityDetail(key)
                    && !RuntimeDiagnostics.acceptsActivity(now.master, now.revision, evaluationRevision)) return;
            if (Objects.equals(status.get(key), value) && status.containsKey(key)) return;
            status.put(key, value); dirty = true;
        }
        worker.removeCallbacks(push); worker.postDelayed(push, 200);
    }
    private void prepareObservers() throws Exception {
        if (observersRegistered) return;
        clearPendingObservers();
        int main = AndroidUsers.mainUser(context.getSystemService(UserManager.class));
        Uri candidate = Uri.parse("content://" + main + "@" + Constants.AUTHORITY);
        ContentObserver configCandidate = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) {
                // Revoke cached eligibility before the asynchronous configuration read.
                synchronized (connectionLock) { invalidateLocked(false); requestRefresh(); }
                notifyChanged();
            }
        };
        ContentObserver statusCandidate = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) { revokeReadiness(false); }
            @Override public void onChange(boolean selfChange, Uri uri) {
                revokeReadiness(uri != null && "ready".equals(uri.getLastPathSegment()));
            }
        };
        configObserver = configCandidate; statusObserver = process.endsWith(":daemon") ? statusCandidate : null;
        try {
            context.getContentResolver().registerContentObserver(candidate.buildUpon().appendPath("config").build(), false, configCandidate);
            if (statusObserver != null) context.getContentResolver().registerContentObserver(candidate.buildUpon().appendPath("readiness").build(), true, statusCandidate);
        } catch (Throwable error) {
            try { clearPendingObservers(); } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            throw new IllegalStateException("Bridge observer registration failed", error);
        }
        root = candidate;
        observersRegistered = true;
    }
    private void revokeReadiness(boolean readyEvent) {
        synchronized (connectionLock) {
            invalidation.invalidate(); snapshot = snapshot.withoutReadiness();
            if (readyEvent) requestRefresh();
            else cancelRefreshLocked();
        }
        notifyChanged();
    }
    private void clearPendingObservers() {
        Throwable error = null;
        if (configObserver != null) {
            try { context.getContentResolver().unregisterContentObserver(configObserver); configObserver = null; }
            catch (Throwable failed) { error = failed; }
        }
        if (statusObserver != null) {
            try { context.getContentResolver().unregisterContentObserver(statusObserver); statusObserver = null; }
            catch (Throwable failed) { if (error == null) error = failed; else error.addSuppressed(failed); }
        }
        if (error != null) throw new IllegalStateException("Bridge observer cleanup pending", error);
    }
    private Bundle identity(IBinder endpoint, long publisherGeneration) {
        Bundle extras = new Bundle(); extras.putString("process", process); extras.putBinder("publisher", publisher);
        if (endpoint != null) { extras.putBinder("provider", endpoint); extras.putLong("publisherGeneration", publisherGeneration); }
        return extras;
    }
    private void refresh() {
        final Object series, attempt;
        final IBinder previous;
        final long previousGeneration;
        synchronized (connectionLock) {
            series = refreshGate.begin();
            if (series == null) return;
            if (!started) { finishRefresh(series, false); return; }
            attempt = invalidation.capture(); previous = provider; previousGeneration = generation;
        }
        try {
            prepareObservers();
            synchronized (connectionLock) {
                if (!invalidation.matches(attempt)) { finishRefresh(series, false); return; }
            }
            boolean connecting = previous == null;
            Bundle result = context.getContentResolver().call(root, connecting ? "connect" : "get_config", null,
                identity(previous, previousGeneration));
            if (result == null || !result.containsKey("config")) throw new IllegalStateException("Invalid bridge response");
            Config loaded = Config.parse(result.getString("config"));
            IBinder endpoint = result.getBinder("provider"), peer = result.getBinder("assistant");
            long receivedGeneration = result.getLong("publisherGeneration", 0);
            long peerGeneration = result.getLong("assistantGeneration", 0);
            if (endpoint == null || !endpoint.isBinderAlive() || receivedGeneration <= 0
                    || (peer != null && peerGeneration <= 0)) throw new IllegalStateException("Bridge identity unavailable");
            boolean configChanged, readinessChanged;
            synchronized (connectionLock) {
                if (!invalidation.matches(attempt)) { finishRefresh(series, false); return; }
                if (!connecting && (provider != endpoint || previousGeneration != receivedGeneration))
                    throw new IllegalStateException("Provider epoch or publisher generation changed");
                if (provider != endpoint) {
                    unlink(provider, providerDeath);
                    providerDeath = () -> {
                        synchronized (connectionLock) {
                            if (provider != endpoint) return;
                            cancelRefreshLocked(); invalidateLocked(true);
                        }
                        notifyChanged(); // Death never starts a provider read or retry.
                    };
                    endpoint.linkToDeath(providerDeath, 0);
                    provider = endpoint;
                }
                generation = receivedGeneration;
                if (assistant != peer || assistantGeneration != peerGeneration) {
                    unlink(assistant, assistantDeath);
                    assistantDeath = () -> {
                        synchronized (connectionLock) {
                            if (assistant != peer || assistantGeneration != peerGeneration) return;
                            cancelRefreshLocked(); invalidation.invalidate();
                            assistant = null; assistantGeneration = 0; snapshot = snapshot.withoutReadiness();
                        }
                        notifyChanged();
                    };
                    if (peer != null) peer.linkToDeath(assistantDeath, 0);
                    assistant = peer; assistantGeneration = peerGeneration;
                }
                if (!endpoint.isBinderAlive()) throw new IllegalStateException("Provider died during connection");
                boolean readiness = result.getBoolean("assistantReady", false) && peer != null && peer.isBinderAlive();
                readinessChanged = readiness != snapshot.assistantReady;
                configChanged = snapshot.provider == null || snapshot.config.revision != loaded.revision
                    || snapshot.config.master != loaded.master;
                synchronized (status) {
                    if (configChanged || !loaded.master) status.keySet().removeIf(RuntimeDiagnostics::isActivityDetail);
                    snapshot = new Snapshot(loaded, endpoint, receivedGeneration, peer, peerGeneration, readiness);
                    status.put("configRevision", loaded.revision); status.put("bridgeError", "");
                    status.put("master", loaded.master);
                    if (configChanged || connecting) dirty = true;
                }
            }
            if (configChanged || connecting || (readinessChanged && process.endsWith(":daemon"))) notifyChanged();
            pushStatus();
            finishRefresh(series, false);
        } catch (Throwable error) {
            synchronized (connectionLock) {
                if (!invalidation.matches(attempt)) { finishRefresh(series, false); return; }
                invalidateLocked(true);
            }
            notifyChanged(); finishRefresh(series, true);
        }
    }
    private void finishRefresh(Object series, boolean failed) {
        synchronized (connectionLock) {
            long delay = refreshGate.complete(series, failed);
            if (delay >= 0) worker.postDelayed(refresh, delay);
            else if (failed) ShoulderHook.log("Config bridge paused until next lifecycle event");
        }
    }
    private void cancelRefreshLocked() { refreshGate.cancel(); worker.removeCallbacks(refresh); }
    private static void unlink(IBinder binder, IBinder.DeathRecipient recipient) {
        if (binder != null && recipient != null) try { binder.unlinkToDeath(recipient, 0); } catch (Throwable ignored) { }
    }
    private void invalidateLocked(boolean disconnect) {
        invalidation.invalidate(); snapshot = Snapshot.empty();
        if (disconnect) {
            unlink(provider, providerDeath); unlink(assistant, assistantDeath);
            provider = null; assistant = null; generation = 0; assistantGeneration = 0;
        }
        synchronized (status) {
            status.put("bridgeError", "连接尚未确认；已暂停扩展"); status.put("master", false);
            status.keySet().removeIf(RuntimeDiagnostics::isActivityDetail); dirty = true;
        }
    }
    private void pushStatus() {
        Snapshot cached = snapshot;
        if (!started || cached.provider == null || !cached.provider.isBinderAlive()) return;
        try {
            JSONObject json;
            synchronized (status) {
                if (!dirty) return;
                if (snapshot != cached) return;
                status.put("master", cached.config.master);
                if (!cached.config.master) status.keySet().removeIf(RuntimeDiagnostics::isActivityDetail);
                json = new JSONObject(new LinkedHashMap<>(status)); dirty = false;
            }
            Bundle extras = identity(cached.provider, cached.generation); extras.putString("status", json.toString());
            Bundle result = context.getContentResolver().call(root, "publish_status", null, extras);
            if (result == null || !result.getBoolean("ok", false)) throw new IllegalStateException("Status not acknowledged");
        } catch (Throwable error) {
            synchronized (connectionLock) {
                if (snapshot != cached) return;
                cancelRefreshLocked(); invalidateLocked(true);
            }
            notifyChanged(); // The next actual lifecycle/config event may reconnect.
        }
    }
}
