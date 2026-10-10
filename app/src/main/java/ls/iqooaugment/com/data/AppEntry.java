package ls.iqooaugment.com.data;

import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.core.JsonSafety;
import org.json.JSONException;
import org.json.JSONObject;

public final class AppEntry {
    public final AppKey key;
    public final String label, space, icon, reason;
    public final boolean eligible;
    public AppEntry(AppKey key, String label, String space, String icon, boolean eligible, String reason) {
        this.key = key;
        this.label = label.length() <= 512 ? label : label.substring(0, Character.isHighSurrogate(label.charAt(511)) ? 511 : 512);
        this.space = space;
        this.icon = icon; this.eligible = eligible; this.reason = reason;
    }
    public static AppEntry fromJson(JSONObject json) throws JSONException {
        return new AppEntry(new AppKey((int) JsonSafety.integer(json.opt("userId"), "userId", 0, Integer.MAX_VALUE),
            JsonSafety.integer(json.opt("serial"), "serial", 0, Long.MAX_VALUE),
            JsonSafety.string(json.opt("pkg"), "pkg", 255)),
            JsonSafety.string(json.opt("label"), "label", 1_000_000),
            JsonSafety.string(json.opt("space"), "space", 128),
            JsonSafety.string(json.opt("icon"), "icon", 44_000),
            JsonSafety.bool(json.opt("eligible"), "eligible"),
            JsonSafety.string(json.opt("reason"), "reason", 2000));
    }
}
