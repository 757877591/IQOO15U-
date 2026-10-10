package ls.iqooaugment.com.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.ProviderAccessPolicy;
import ls.iqooaugment.com.core.JsonSafety;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Read-only configuration bridge. Binder UID1000 is the trust boundary, not a
 * supplied process name or PID. Binder tokens provide epoch/liveness fencing,
 * not authentication of a particular package within that shared UID.
 */
public final class ConfigProvider extends ContentProvider {
    private final IBinder epoch = new Binder();
    private static final Map<String, Publisher> PUBLISHERS = new HashMap<>();
    private static long nextGeneration;
    private final class Publisher implements IBinder.DeathRecipient {
        final String key; final int uid, pid; final IBinder token; final long generation;
        Publisher(String key, int uid, int pid, IBinder token) {
            if (nextGeneration == Long.MAX_VALUE) throw new IllegalStateException("Publisher generation exhausted");
            this.key = key; this.uid = uid; this.pid = pid; this.token = token; this.generation = ++nextGeneration;
        }
        @Override public void binderDied() {
            boolean removed = false;
            synchronized (ConfigStore.STATUS_LOCK) {
                if (PUBLISHERS.get(key) == this) {
                    PUBLISHERS.remove(key); removeStatusLocked(key, uid); removed = true;
                }
            }
            if (removed) notifyState(key.startsWith("assistantui@") ? Boolean.FALSE : null);
        }
    }
    @Override public boolean onCreate() {
        // Starting/opening the app is a real event: existing hooks may make a bounded reconnection.
        new Handler(Looper.getMainLooper()).post(() -> getContext().getContentResolver().notifyChange(
            Uri.parse("content://" + Constants.AUTHORITY + "/config"), null));
        return true;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int caller = Binder.getCallingUid(), pid = Binder.getCallingPid();
        boolean self = caller == Process.myUid();
        if (!ProviderAccessPolicy.canReadConfig(caller, Process.myUid())) throw new SecurityException("Untrusted Binder UID");
        if ("connect".equals(method) || "get_config".equals(method)) {
            Bundle out = new Bundle(); boolean replaced = false; String connectedCategory = null;
            synchronized (ConfigStore.STATUS_LOCK) {
                if (!self) {
                    if (!ProviderAccessPolicy.canPublishStatus(caller)) throw new SecurityException("Untrusted publisher");
                    connectedCategory = category(extras);
                    String key = connectedCategory + "@u" + caller / 100000;
                    IBinder token = extras.getBinder("publisher");
                    if (token == null || !token.isBinderAlive() || pid <= 0) throw new SecurityException("Publisher token unavailable");
                    Publisher previous = PUBLISHERS.get(key);
                    if ("connect".equals(method)) {
                        if (previous == null || previous.token != token || previous.pid != pid || previous.uid != caller) {
                            Publisher publisher = new Publisher(key, caller, pid, token);
                            try { token.linkToDeath(publisher, 0); }
                            catch (android.os.RemoteException error) { throw new SecurityException("Publisher already dead", error); }
                            if (!token.isBinderAlive()) { token.unlinkToDeath(publisher, 0); throw new SecurityException("Publisher died during registration"); }
                            if (previous != null) previous.token.unlinkToDeath(previous, 0);
                            PUBLISHERS.put(key, publisher); removeStatusLocked(key, caller); replaced = true;
                        }
                    } else requireConnection(key, caller, pid, extras);
                    out.putLong("publisherGeneration", requirePublisher(key, caller, pid, token).generation);
                }
                try {
                    Config config = new ConfigStore(getContext()).load();
                    out.putString("config", (self ? config : hookConfig(config)).encode());
                    out.putBinder("provider", epoch);
                    String assistantKey = "assistantui@u" + caller / 100000;
                    JSONObject assistant = new JSONObject(ConfigStore.readStatus(getContext(), assistantKey));
                    Publisher peer = PUBLISHERS.get(assistantKey);
                    if (peer != null && peer.token.isBinderAlive()) {
                        out.putBinder("assistant", peer.token); out.putLong("assistantGeneration", peer.generation);
                    }
                    out.putBoolean("assistantReady", RuntimeStatus.assistantReady(getContext(), config, assistant));
                } catch (Exception error) { out.putString("error", "配置读取失败"); }
            }
            if (replaced) notifyState("assistantui".equals(connectedCategory) ? Boolean.FALSE : null);
            return out;
        }
        if ("publish_status".equals(method) && ProviderAccessPolicy.canPublishStatus(caller) && extras != null) {
            String category = category(extras), key = category + "@u" + caller / 100000;
            if (extras.getBinder("provider") != epoch) throw new SecurityException("Stale provider epoch");
            String raw = extras.getString("status", "");
            JsonSafety.requireDocument(raw, 24_000, 8);
            try {
                JSONObject report = new JSONObject(raw);
                if (!ProviderAccessPolicy.matchingDiagnosticPid(pid, report.opt("pid"))) throw new SecurityException("Diagnostic PID mismatch");
                boolean changed, readinessChanged, afterReady;
                synchronized (ConfigStore.STATUS_LOCK) {
                    Publisher publisher = requireConnection(key, caller, pid, extras);
                    Config current = new ConfigStore(getContext()).load();
                    JSONObject old = new JSONObject(ConfigStore.readStatus(getContext(), key));
                    boolean beforeReady = RuntimeStatus.assistantReady(getContext(), current, old);
                    JSONObject status = allowedDiagnostics(report);
                    status.put("pid", pid); status.put("process", extras.getString("process"));
                    status.put("callerUid", caller); status.put("processUser", caller / 100000);
                    status.put("publisherGeneration", publisher.generation);
                    status.put("identityBoundary", "android-system-shared-uid; category is self-reported");
                    status.put("receivedElapsed", SystemClock.elapsedRealtime());
                    status.put("receivedWall", System.currentTimeMillis());
                    boolean matches = ProviderAccessPolicy.matchesCurrentConfig(current.master, current.revision,
                        report.opt("master"), report.opt("configRevision"));
                    if (!ProviderAccessPolicy.mayRetainActivity(current.master, current.revision, report.opt("master"), report.opt("configRevision")))
                        status = ConfigStore.redactActivityStatus(status);
                    status.put("master", current.master); status.put("configPending", !matches);
                    changed = ConfigStore.publishStatusLocked(getContext(), key, status);
                    if (caller / 100000 == Process.myUid() / 100000) changed |= ConfigStore.publishStatusLocked(getContext(), category, status);
                    afterReady = RuntimeStatus.assistantReady(getContext(), current, status);
                    readinessChanged = category.equals("assistantui") && beforeReady != afterReady;
                }
                if (changed || readinessChanged) notifyState(readinessChanged ? Boolean.valueOf(afterReady) : null);
                Bundle out = new Bundle(); out.putBoolean("ok", true); return out;
            } catch (org.json.JSONException | java.io.IOException error) { throw new IllegalArgumentException("Invalid configuration or status", error); }
        }
        throw new SecurityException("Operation not available");
    }
    private static String category(Bundle extras) {
        if (extras == null) throw new SecurityException("Missing publisher identity");
        try { return ProviderAccessPolicy.statusCategory(extras.getString("process", "")); }
        catch (IllegalArgumentException error) { throw new SecurityException("Unknown status category"); }
    }
    private Publisher requireConnection(String key, int uid, int pid, Bundle extras) {
        if (extras == null || extras.getBinder("provider") != epoch) throw new SecurityException("Stale provider epoch");
        Publisher registered = requirePublisher(key, uid, pid, extras.getBinder("publisher"));
        if (extras.getLong("publisherGeneration", 0) != registered.generation)
            throw new SecurityException("Stale publisher generation");
        return registered;
    }
    private static Publisher requirePublisher(String key, int uid, int pid, IBinder token) {
        Publisher registered = PUBLISHERS.get(key);
        if (registered == null || token == null || registered.token != token || registered.uid != uid
                || registered.pid != pid || !token.isBinderAlive()) throw new SecurityException("Unregistered or stale publisher");
        return registered;
    }
    private static void removeStatusLocked(String key, int uid) {
        ConfigStore.removeStatusLocked(key);
        if (uid / 100000 == Process.myUid() / 100000) ConfigStore.removeStatusLocked(key.substring(0, key.indexOf('@')));
    }
    /** Called only in this app process under STATUS_LOCK; status is never an authorization token. */
    static boolean liveStatus(JSONObject status) {
        synchronized (ConfigStore.STATUS_LOCK) {
            try {
                int uid = status.getInt("callerUid"), pid = status.getInt("pid");
                String key = ProviderAccessPolicy.statusCategory(status.getString("process")) + "@u" + uid / 100000;
                Publisher publisher = PUBLISHERS.get(key);
                return publisher != null && publisher.uid == uid && publisher.pid == pid && publisher.token.isBinderAlive()
                    && publisher.generation == status.getLong("publisherGeneration");
            } catch (Exception error) { return false; }
        }
    }
    private void notifyState(Boolean readiness) {
        getContext().getContentResolver().notifyChange(Uri.parse("content://" + Constants.AUTHORITY + "/status"), null);
        if (readiness != null) getContext().getContentResolver().notifyChange(
            Uri.parse("content://" + Constants.AUTHORITY + "/readiness/" + (readiness ? "ready" : "revoked")), null);
    }
    private static Config hookConfig(Config source) {
        List<Target> targets = new ArrayList<>();
        for (Target target : source.targets) {
            if (ProviderAccessPolicy.discloseTargetToHook(source.master, target.enabled)) {
                targets.add(new Target(target.key, "", "", true));
            }
        }
        return new Config(source.master, source.revision, targets);
    }
    private static JSONObject allowedDiagnostics(JSONObject source) throws org.json.JSONException {
        JSONObject result = new JSONObject(); Iterator<String> keys = source.keys();
        while (keys.hasNext()) { String key = keys.next(); Object value = source.opt(key);
            if (ProviderAccessPolicy.isAllowedDiagnosticValue(key, value)) result.put(key, value); }
        return result;
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { throw new SecurityException("Use call"); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new SecurityException("Read-only"); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new SecurityException("Read-only"); }
}
