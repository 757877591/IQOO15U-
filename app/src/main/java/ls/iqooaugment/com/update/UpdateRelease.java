package ls.iqooaugment.com.update;

import ls.iqooaugment.com.BuildConfig;
import ls.iqooaugment.com.core.JsonSafety;
import ls.iqooaugment.com.core.UpdatePolicy;
import org.json.JSONObject;
import java.time.Instant;

public final class UpdateRelease {
    public final long versionCode, size;
    public final int minSdk;
    public final String versionName, notes, url, sha256, certificateSha256;
    final String document;
    private UpdateRelease(JSONObject release, String document) throws Exception {
        versionCode = JsonSafety.integer(release.get("versionCode"), "versionCode", 1, Integer.MAX_VALUE);
        versionName = required(release, "versionName", 100);
        minSdk = (int) JsonSafety.integer(release.get("minSdk"), "minSdk", 1, 1000);
        String published = required(release, "publishedAt", 64);
        if (!published.endsWith("Z")) throw new IllegalArgumentException("发布时间必须使用 UTC");
        Instant.parse(published);
        notes = JsonSafety.string(release.get("notes"), "notes", 12000);
        JSONObject apk = release.getJSONObject("apk");
        url = UpdatePolicy.apkUrl(required(apk, "url", 2048)).toString();
        size = JsonSafety.integer(apk.get("size"), "size", 1, UpdatePolicy.MAX_APK_BYTES);
        sha256 = UpdatePolicy.digest(required(apk, "sha256", 64));
        certificateSha256 = UpdatePolicy.digest(required(apk, "certificateSha256", 64));
        this.document = document;
    }
    public static UpdateRelease parse(String raw) throws Exception {
        JsonSafety.requireDocument(raw, UpdatePolicy.MAX_MANIFEST_BYTES, 8);
        JSONObject root = new JSONObject(raw);
        if (JsonSafety.integer(root.get("schemaVersion"), "schemaVersion", 1, 1) != 1
                || !"iqoo-shoulder".equals(required(root, "appId", 64))
                || !BuildConfig.APPLICATION_ID.equals(required(root, "packageName", 200)))
            throw new IllegalArgumentException("更新清单与当前软件不匹配");
        Object release = root.get("release");
        if (release == JSONObject.NULL) return null;
        if (!(release instanceof JSONObject)) throw new IllegalArgumentException("更新清单格式无效");
        return new UpdateRelease((JSONObject) release, raw);
    }
    private static String required(JSONObject object, String name, int limit) throws Exception {
        String value = JsonSafety.string(object.get(name), name, limit);
        if (value.trim().isEmpty()) throw new IllegalArgumentException("更新信息缺少 " + name);
        return value;
    }
}
