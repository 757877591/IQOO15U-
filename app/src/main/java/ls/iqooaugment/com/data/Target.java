package ls.iqooaugment.com.data;

import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.core.JsonSafety;
import org.json.JSONException;
import org.json.JSONObject;

public final class Target {
    public final AppKey key;
    public final String label, space;
    public final boolean enabled;
    public Target(AppKey key, String label, String space, boolean enabled) {
        if (key == null) throw new IllegalArgumentException("Missing target identity");
        this.key = key;
        // Labels come from installed apps, not the authorization identity. Bound them before persistence.
        this.label = display(label, key.packageName, 512);
        this.space = display(space, "空间", 128);
        this.enabled = enabled;
    }
    private static String display(String value, String fallback, int limit) {
        if (value == null) return fallback;
        if (value.length() <= limit) return value;
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end);
    }
    public Target withEnabled(boolean value) { return new Target(key, label, space, value); }
    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("userId", key.userId).put("serial", key.serial)
            .put("pkg", key.packageName).put("label", label).put("space", space).put("enabled", enabled);
    }
    public static Target fromJson(JSONObject json) throws JSONException {
        String pkg = JsonSafety.string(json.opt("pkg"), "pkg", 255);
        return new Target(new AppKey((int) JsonSafety.integer(json.opt("userId"), "userId", 0, Integer.MAX_VALUE),
            JsonSafety.integer(json.opt("serial"), "serial", 0, Long.MAX_VALUE), pkg),
            json.has("label") ? JsonSafety.string(json.opt("label"), "label", 512) : pkg,
            json.has("space") ? JsonSafety.string(json.opt("space"), "space", 128) : "空间",
            JsonSafety.bool(json.opt("enabled"), "enabled"));
    }
}
