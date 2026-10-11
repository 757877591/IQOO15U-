package ls.iqooaugment.com.update;

import ls.iqooaugment.com.core.UpdatePolicy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

final class UpdateNetwork {
    static final String FEED = "https://raw.githubusercontent.com/liligit1815/lispacerelease/main/updates/iqoo-shoulder.json";
    interface Progress { void changed(long bytes); }
    private UpdateNetwork() { }
    static String manifest() throws Exception {
        HttpsURLConnection connection = open(URI.create(FEED), false);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            long length = connection.getContentLengthLong();
            if (length > UpdatePolicy.MAX_MANIFEST_BYTES) throw new IOException("更新清单过大");
            byte[] buffer = new byte[4096]; int count;
            long deadline = System.nanoTime() + 45_000_000_000L;
            while ((count = input.read(buffer)) != -1) {
                ensureRunning(deadline);
                if (output.size() > UpdatePolicy.MAX_MANIFEST_BYTES - count) throw new IOException("更新清单过大");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally { connection.disconnect(); }
    }
    static void download(UpdateRelease release, File target, Progress progress) throws Exception {
        HttpsURLConnection connection = open(UpdatePolicy.apkUrl(release.url), true);
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
            long length = connection.getContentLengthLong();
            if (length >= 0 && length != release.size) throw new IOException("服务器安装包大小与发布信息不一致");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32768]; int count; long total = 0, previous = 0;
            long deadline = System.nanoTime() + 900_000_000_000L;
            while ((count = input.read(buffer)) != -1) {
                ensureRunning(deadline);
                total = UpdatePolicy.checkedLength(total, count, release.size);
                output.write(buffer, 0, count); digest.update(buffer, 0, count);
                long now = System.nanoTime();
                if (now - previous > 300_000_000L) { progress.changed(total); previous = now; }
            }
            if (total != release.size || !hex(digest.digest()).equals(release.sha256))
                throw new IOException("安装包下载不完整或 SHA-256 校验失败");
            output.getFD().sync();
        } finally { connection.disconnect(); }
    }
    private static HttpsURLConnection open(URI uri, boolean download) throws Exception {
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "IQOOShoulderExtension-Update/1");
            connection.setRequestProperty("Accept", download ? "application/octet-stream" : "application/json");
            connection.setUseCaches(false);
            try {
                int code = connection.getResponseCode();
                if (code == 200) return connection;
                if (download && (code == 301 || code == 302 || code == 303 || code == 307 || code == 308)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("下载地址跳转无效");
                    uri = UpdatePolicy.redirect(uri.resolve(location).toString());
                    connection.disconnect(); continue;
                }
                throw new IOException(code == 404 ? "更新信息暂未发布" : "GitHub 请求失败（HTTP " + code + "）");
            } catch (Exception e) { connection.disconnect(); throw e; }
        }
        throw new IOException("下载地址跳转过多");
    }
    private static void ensureRunning(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("下载已取消");
        if (System.nanoTime() > deadline) throw new IOException("网络请求超时，请稍后重试");
    }
    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) { int b = value & 255; text.append(Character.forDigit(b >>> 4, 16)).append(Character.forDigit(b & 15, 16)); }
        return text.toString();
    }
}
