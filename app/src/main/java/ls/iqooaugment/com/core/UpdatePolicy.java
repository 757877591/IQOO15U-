package ls.iqooaugment.com.core;

import java.net.URI;
import java.util.Locale;

/** Trust and resource limits for the public release feed, independent of Android UI. */
public final class UpdatePolicy {
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    public static final long MAX_APK_BYTES = 512L * 1024 * 1024;
    public static final String DOWNLOAD_PREFIX = "/liligit1815/lispacerelease/releases/download/";
    private UpdatePolicy() { }

    public static boolean shouldAutomaticallyCheck(long now, long lastAttempt) {
        return lastAttempt <= 0 || (now >= lastAttempt && now - lastAttempt >= 24L * 60 * 60 * 1000);
    }

    public static URI https(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawFragment() != null) throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) { throw new IllegalArgumentException("更新地址无效或不安全"); }
    }

    public static URI apkUrl(String value) {
        URI uri = https(value);
        String path = uri.getRawPath();
        if (!"github.com".equalsIgnoreCase(uri.getHost()) || uri.getRawQuery() != null
                || path == null || !path.startsWith(DOWNLOAD_PREFIX)
                || path.substring(DOWNLOAD_PREFIX.length()).split("/", -1).length != 2
                || path.contains("%") || path.contains("\\") || path.contains("..")
                || !path.matches("/liligit1815/lispacerelease/releases/download/[A-Za-z0-9._+-]+/[A-Za-z0-9._+-]+\\.apk"))
            throw new IllegalArgumentException("安装包地址不属于指定 GitHub 更新仓库");
        return uri;
    }

    public static URI redirect(String value) {
        URI uri = https(value);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        // GitHub signs redirects to its official asset hosts; never follow arbitrary mirrors.
        if (!host.endsWith(".githubusercontent.com"))
            throw new IllegalArgumentException("下载跳转不是 GitHub 官方地址");
        return uri;
    }

    public static String digest(String value) {
        if (value == null || !value.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("更新校验信息无效");
        return value.toLowerCase(Locale.ROOT);
    }

    public static long checkedLength(long current, int count, long expected) {
        if (count < 0 || expected < 1 || expected > MAX_APK_BYTES
                || current < 0 || current > expected - count)
            throw new IllegalArgumentException("安装包大小与发布信息不一致");
        return current + count;
    }
}
