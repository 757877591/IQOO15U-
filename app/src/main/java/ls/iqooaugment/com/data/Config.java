package ls.iqooaugment.com.data;

import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.AdmissionPolicy;
import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.core.JsonSafety;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Immutable versioned snapshot. Bad/unknown schemas fail closed instead of partially applying. */
public final class Config {
    public final boolean master;
    public final long revision;
    public final List<Target> targets;
    public final Set<AppKey> enabled;
    public Config(boolean master, long revision, List<Target> targets) {
        if (revision < 0 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid revision");
        if (targets == null || targets.size() > Constants.MAX_TARGETS) throw new IllegalArgumentException("Invalid targets");
        LinkedHashMap<AppKey, Target> unique = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target == null) throw new IllegalArgumentException("Missing target");
            if (!AdmissionPolicy.allowedPackage(target.key.packageName)) throw new IllegalArgumentException("Protected package");
            if (unique.put(target.key, target) != null) throw new IllegalArgumentException("Duplicate target identity");
        }
        this.master = master; this.revision = revision;
        this.targets = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        LinkedHashSet<AppKey> active = new LinkedHashSet<>();
        for (Target t : this.targets) if (t.enabled) active.add(t.key);
        enabled = Collections.unmodifiableSet(active);
    }
    public static Config empty() { return new Config(false, 0, Collections.emptyList()); }
    public static Config parse(String value) throws JSONException {
        JsonSafety.requireDocument(value, 1_000_000, 8);
        JSONObject json = new JSONObject(value);
        JsonSafety.integer(json.opt("schema"), "schema", Constants.SCHEMA, Constants.SCHEMA);
        JSONArray array = json.getJSONArray("targets");
        if (array.length() > Constants.MAX_TARGETS) throw new JSONException("Too many targets");
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) targets.add(Target.fromJson(array.getJSONObject(i)));
        return new Config(JsonSafety.bool(json.opt("master"), "master"),
            JsonSafety.integer(json.opt("revision"), "revision", 0, Long.MAX_VALUE - 1), targets);
    }
    public String encode() throws JSONException {
        JSONArray array = new JSONArray();
        for (Target t : targets) array.put(t.toJson());
        return new JSONObject().put("schema", Constants.SCHEMA).put("master", master)
            .put("revision", revision).put("targets", array).toString();
    }
    public Config withMaster(boolean value) { return new Config(value, Math.addExact(revision, 1), targets); }
    public Config withTargets(List<Target> value) { return new Config(master, Math.addExact(revision, 1), value); }
    public Target find(AppKey key) { for (Target t : targets) if (t.key.equals(key)) return t; return null; }
}
