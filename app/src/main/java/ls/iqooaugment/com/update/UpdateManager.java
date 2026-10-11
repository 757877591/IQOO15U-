package ls.iqooaugment.com.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ls.iqooaugment.com.BuildConfig;
import ls.iqooaugment.com.core.UpdatePolicy;

/** App-process updater. No hooks, root work, services, background schedules or telemetry. */
public final class UpdateManager {
    public interface Listener {
        void updateChanged();
        void offerUpdate(UpdateRelease release);
        void updateMessage(String title, String message);
        void installUpdate(Uri uri);
    }
    static final String PREFS = "release_updates";
    private static UpdateManager instance;
    public static synchronized UpdateManager get(Context context) {
        if (instance == null) instance = new UpdateManager(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WeakReference<Listener> listener = new WeakReference<>(null);
    private Future<?> task;
    private int generation;
    private boolean checking, downloading, verifying, manualCheck, installPending;
    private long downloaded, total;
    private UpdateRelease offered, ready;
    private String pendingTitle, pendingMessage;
    private UpdateManager(Context context) {
        this.context = context; preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            String document = preferences.getString("readyDocument", "");
            if (!document.isEmpty()) {
                UpdateRelease saved = UpdateRelease.parse(document);
                if (saved != null && saved.versionCode > BuildConfig.VERSION_CODE && apk(saved).isFile()) ready = saved;
            }
        } catch (Exception ignored) { }
        if (ready == null) preferences.edit().remove("readyDocument").remove("readyHash").remove("token").remove("awaitPermission").apply();
    }
    public void attach(Listener value) { listener = new WeakReference<>(value); deliver(); }
    public void detach(Listener value) { if (listener.get() == value) listener.clear(); }
    public boolean busy() { return checking || downloading || verifying; }
    public boolean downloading() { return downloading; }
    public boolean hasReady() { return ready != null; }
    public boolean isReady(UpdateRelease release) { return ready != null && ready.sha256.equals(release.sha256); }
    public String status() {
        if (checking) return "正在检查更新…";
        if (verifying) return "正在校验安装包…";
        if (downloading) return "正在下载 " + (total == 0 ? 0 : downloaded * 100 / total) + "%";
        if (ready != null) return "已下载 " + ready.versionName + "，点击安装";
        return "GitHub 更新源 · 每天首次打开时自动检查";
    }
    public void automaticCheck() {
        long now = System.currentTimeMillis();
        long lastAttempt = preferences.getLong("checkedAt", 0);
        if (busy()) return;
        // Rebase the waiting period after clock rollback, without an extra network attempt.
        if (now < lastAttempt) { preferences.edit().putLong("checkedAt", now).commit(); return; }
        if (!UpdatePolicy.shouldAutomaticallyCheck(now, lastAttempt)) return;
        preferences.edit().putLong("checkedAt", now).commit();
        check(false);
    }
    public void check(boolean manual) {
        if (checking) { manualCheck |= manual; return; }
        if (downloading || verifying) return;
        checking = true; manualCheck = manual; int request = ++generation; changed();
        task = worker.submit(() -> {
            UpdateRelease release = null; String error = null;
            try { release = UpdateRelease.parse(UpdateNetwork.manifest()); }
            catch (Exception e) { error = error(e); }
            UpdateRelease result = release; String failure = error;
            main.post(() -> {
                if (request != generation) return;
                checking = false;
                if (failure != null) { if (manualCheck) message("检查更新失败", failure); }
                else if (result == null) { if (manualCheck) message("检查更新", "更新源暂未发布安装包。"); }
                else if (result.versionCode <= BuildConfig.VERSION_CODE) { if (manualCheck) message("检查更新", "当前已是最新版本。"); }
                else if (result.minSdk > Build.VERSION.SDK_INT) { if (manualCheck) message("暂不能更新", "新版本需要 Android SDK " + result.minSdk + " 或更高版本。"); }
                else { offered = result; deliver(); }
                manualCheck = false; changed();
            });
        });
    }
    public void dismissOffer() { offered = null; }
    public void download(UpdateRelease release) {
        if (busy()) return;
        offered = null;
        if (isReady(release)) { installReady(); return; }
        downloading = true; downloaded = 0; total = release.size; int request = ++generation; changed();
        task = worker.submit(() -> {
            File partial = new File(directory(), UUID.randomUUID() + ".part");
            try {
                if (!directory().isDirectory() && !directory().mkdirs()) throw new IOException("无法创建更新下载目录");
                if (directory().getUsableSpace() < release.size + 1024 * 1024) throw new IOException("可用存储空间不足");
                UpdateNetwork.download(release, partial, bytes -> main.post(() -> {
                    if (request == generation && downloading) { downloaded = bytes; changed(); }
                }));
                verify(partial, release);
                if (Thread.currentThread().isInterrupted()) throw new IOException("下载已取消");
                File finalApk = apk(release);
                if (finalApk.exists() && !finalApk.delete()) throw new IOException("无法替换旧更新包");
                if (!partial.renameTo(finalApk)) throw new IOException("无法保存更新安装包");
                main.post(() -> {
                    if (request != generation) return;
                    downloading = false; ready = release;
                    preferences.edit().putString("readyDocument", release.document).putString("readyHash", release.sha256)
                            .putString("token", UUID.randomUUID().toString()).commit();
                    installPending = true; changed(); deliver();
                });
            } catch (Exception e) {
                String failure = error(e);
                main.post(() -> { if (request == generation) { downloading = false; message("下载更新失败", failure); changed(); } });
            } finally { if (partial.exists()) partial.delete(); }
        });
    }
    public void cancelDownload() {
        if (!downloading) return;
        ++generation; downloading = false; installPending = false;
        if (task != null) task.cancel(true);
        changed();
    }
    /** Revalidate persisted APK before every install attempt, including after process recreation. */
    public void installReady() {
        if (busy() || ready == null) return;
        UpdateRelease release = ready; verifying = true; int request = ++generation; changed();
        task = worker.submit(() -> {
            try {
                verify(apk(release), release);
                main.post(() -> { if (request == generation) { verifying = false; installPending = true; changed(); deliver(); } });
            } catch (Exception e) {
                String failure = error(e);
                main.post(() -> { if (request == generation) {
                    verifying = false; ready = null;
                    preferences.edit().remove("readyDocument").remove("readyHash").remove("token").remove("awaitPermission").apply();
                    message("安装包校验失败", failure + "\n请重新检查更新并下载。"); changed();
                } });
            }
        });
    }
    public void awaitingPermission(boolean value) { preferences.edit().putBoolean("awaitPermission", value).commit(); }
    public boolean awaitingPermission() { return preferences.getBoolean("awaitPermission", false); }
    public void message(String title, String text) { pendingTitle = title; pendingMessage = text; deliver(); }
    private void changed() { Listener value = listener.get(); if (value != null) value.updateChanged(); }
    private void deliver() {
        Listener value = listener.get(); if (value == null) return;
        if (pendingMessage != null) {
            String title = pendingTitle, text = pendingMessage; pendingMessage = null; value.updateMessage(title, text);
        } else if (installPending && ready != null) {
            installPending = false;
            String token = preferences.getString("token", "");
            value.installUpdate(new Uri.Builder().scheme("content").authority(BuildConfig.APPLICATION_ID + ".updates")
                    .appendPath(token).appendPath("update.apk").build());
        } else if (offered != null) value.offerUpdate(offered);
    }
    private File directory() { return new File(context.getFilesDir(), "updates"); }
    private File apk(UpdateRelease release) { return new File(directory(), release.sha256 + ".apk"); }
    private void verify(File file, UpdateRelease release) throws Exception {
        if (release.minSdk > Build.VERSION.SDK_INT || release.versionCode <= BuildConfig.VERSION_CODE)
            throw new IOException("安装包版本不适用于当前设备");
        if (!file.isFile() || file.length() != release.size) throw new IOException("安装包大小不正确");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new IOException("操作已取消"); digest.update(buffer, 0, count); }
        }
        if (!UpdateNetwork.hex(digest.digest()).equals(release.sha256)) throw new IOException("安装包 SHA-256 校验失败");
        PackageManager packages = context.getPackageManager();
        PackageInfo candidate = packages.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = packages.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)
                || candidate.getLongVersionCode() != release.versionCode || !release.versionName.equals(candidate.versionName)
                || candidate.applicationInfo == null || candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT
                || candidate.applicationInfo.minSdkVersion != release.minSdk
                || candidate.signingInfo == null || installed.signingInfo == null)
            throw new IOException("安装包身份、版本或系统要求不匹配");
        Signature[] incoming = candidate.signingInfo.getApkContentsSigners();
        Signature[] current = installed.signingInfo.getApkContentsSigners();
        if (incoming.length != 1 || current.length != 1 || !Arrays.equals(incoming[0].toByteArray(), current[0].toByteArray())
                || !UpdateNetwork.hex(MessageDigest.getInstance("SHA-256").digest(incoming[0].toByteArray())).equals(release.certificateSha256))
            throw new IOException("安装包签名与已安装版本不一致");
    }
    private static String error(Exception exception) {
        if (exception instanceof UnknownHostException) return "无法连接 GitHub，请检查网络后重试。";
        if (exception instanceof SocketTimeoutException) return "连接 GitHub 超时，请稍后重试。";
        if (exception instanceof IOException || exception instanceof IllegalArgumentException)
            return exception.getMessage() == null ? "网络或更新信息异常，请稍后重试。" : exception.getMessage();
        return "更新信息或安装包无效，请稍后重试。";
    }
}
