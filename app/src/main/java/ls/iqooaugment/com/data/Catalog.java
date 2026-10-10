package ls.iqooaugment.com.data;

import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.core.ScanPolicy;
import ls.iqooaugment.com.core.AdmissionPolicy;
import ls.iqooaugment.com.core.JsonSafety;
import ls.iqooaugment.com.core.RootProtocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Catalog {
    public static final class Space {
        public final int id; public final long serial;
        public final String title, warning;
        public final ScanPolicy.Kind kind;
        public final boolean unlocked, scanned, defaultSelected;
        Space(JSONObject j) throws JSONException {
            id = (int) JsonSafety.integer(j.opt("id"), "id", 0, Integer.MAX_VALUE);
            serial = JsonSafety.integer(j.opt("serial"), "serial", -1, Long.MAX_VALUE);
            title = JsonSafety.string(j.opt("title"), "title", 128);
            kind = ScanPolicy.Kind.valueOf(JsonSafety.string(j.opt("kind"), "kind", 32));
            warning = JsonSafety.string(j.opt("warning"), "warning", 2000);
            unlocked = JsonSafety.bool(j.opt("unlocked"), "unlocked");
            scanned = JsonSafety.bool(j.opt("scanned"), "scanned");
            if (scanned && (!unlocked || serial < 0)) throw new JSONException("Invalid scanned space");
            defaultSelected = ScanPolicy.defaultSelected(kind, unlocked, serial);
        }
    }
    public final List<Space> spaces;
    public final List<AppEntry> apps;
    public final int mainUser;
    public final String cubeVersion;
    public final long cubeCode;
    public final boolean compatible, appsRead;
    public Catalog(String raw) throws JSONException {
        JsonSafety.requireDocument(raw, RootProtocol.MAX_OUTPUT, 8);
        JSONObject j = new JSONObject(raw);
        if (!JsonSafety.bool(j.opt("ok"), "ok"))
            throw new JSONException(j.has("error") ? JsonSafety.string(j.opt("error"), "error", 2000) : "读取失败");
        mainUser = (int) JsonSafety.integer(j.opt("mainUser"), "mainUser", 0, Integer.MAX_VALUE);
        cubeVersion = j.has("cubeVersion") ? JsonSafety.string(j.opt("cubeVersion"), "cubeVersion", 256) : "未安装";
        cubeCode = JsonSafety.integer(j.opt("cubeCode"), "cubeCode", -1, Long.MAX_VALUE);
        appsRead = JsonSafety.bool(j.opt("appsRead"), "appsRead");
        compatible = cubeCode == Constants.CUBE_CODE && Constants.CUBE_VERSION.equals(cubeVersion);
        JSONArray users = j.getJSONArray("users");
        if (users.length() > ScanPolicy.MAX_SPACES) throw new JSONException("Too many spaces");
        List<Space> parsedSpaces = new ArrayList<>();
        Map<Integer, Space> byUser = new HashMap<>();
        for (int i = 0; i < users.length(); i++) {
            Space space = new Space(users.getJSONObject(i));
            if (byUser.put(space.id, space) != null) throw new JSONException("Duplicate space");
            if (!appsRead && space.scanned) throw new JSONException("Unexpected space scan");
            parsedSpaces.add(space);
        }
        if (!byUser.containsKey(mainUser)) throw new JSONException("Unknown main space");
        JSONArray list = j.getJSONArray("apps");
        if (list.length() > 4000 || (!appsRead && list.length() != 0)) throw new JSONException("Invalid app count");
        List<AppEntry> parsedApps = new ArrayList<>();
        Set<AppKey> identities = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            AppEntry app = AppEntry.fromJson(list.getJSONObject(i));
            Space space = byUser.get(app.key.userId);
            if (space == null || !space.scanned || !space.unlocked || space.serial != app.key.serial
                    || !space.title.equals(app.space) || !AdmissionPolicy.allowedPackage(app.key.packageName)
                    || !identities.add(app.key)) throw new JSONException("Invalid app space or identity");
            parsedApps.add(app);
        }
        spaces = Collections.unmodifiableList(parsedSpaces);
        apps = Collections.unmodifiableList(parsedApps);
    }
    /** Verify the response against the exact request, not just its self-described scope. */
    public void validateScope(Set<ScanPolicy.SpaceKey> selected) {
        if (appsRead != (selected != null)) throw new IllegalArgumentException("Unexpected scan operation");
        Set<ScanPolicy.SpaceKey> available = new HashSet<>();
        for (Space space : spaces) {
            if (space.serial >= 0) available.add(new ScanPolicy.SpaceKey(space.id, space.serial));
            if (space.scanned && !ScanPolicy.mayScan(selected, space.id, space.serial, space.unlocked))
                throw new IllegalArgumentException("Unrequested space in Root result");
        }
        if (selected != null) ScanPolicy.validateSelection(selected, available);
        for (AppEntry app : apps)
            if (!ScanPolicy.mayScan(selected, app.key.userId, app.key.serial, true))
                throw new IllegalArgumentException("Unrequested app in Root result");
    }
    public AppEntry find(AppKey key) { for (AppEntry e : apps) if (e.key.equals(key)) return e; return null; }
    public String spaceTitle(int id) { for (Space s : spaces) if (s.id == id) return s.title; return "空间 u" + id; }
    public boolean completeFor(int id) { for (Space s : spaces) if (s.id == id) return s.scanned && s.unlocked && s.warning.isEmpty(); return false; }
}
