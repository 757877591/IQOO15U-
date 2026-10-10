import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.*;
import ls.iqooaugment.com.data.*;
import java.util.*;
import org.json.*;

/** Run with a functional Android libcore JSON jar, not SDK android.jar stubs. */
public final class DataParsingTests {
    private static int checks;
    private interface Operation { void run() throws Exception; }
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    private static void rejects(Operation operation, String name) throws Exception {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException | JSONException expected) { rejected = true; }
        check(rejected, name);
    }
    private static JSONObject app(int user, long serial, String pkg, String label) throws Exception {
        return new JSONObject().put("userId", user).put("serial", serial).put("pkg", pkg).put("label", label)
            .put("space", "主空间").put("icon", "").put("eligible", true).put("reason", "");
    }
    private static JSONObject catalog(boolean scan, String label) throws Exception {
        JSONObject space = new JSONObject().put("id", 0).put("serial", 0).put("title", "主空间")
            .put("kind", "MAIN").put("warning", "").put("unlocked", true).put("scanned", scan);
        return new JSONObject().put("ok", true).put("mainUser", 0).put("cubeVersion", Constants.CUBE_VERSION)
            .put("cubeCode", Constants.CUBE_CODE).put("appsRead", scan).put("users", new JSONArray().put(space))
            .put("apps", scan ? new JSONArray().put(app(0, 0, "com.example.hostile", label)) : new JSONArray());
    }
    public static void main(String[] args) throws Exception {
        Set<ScanPolicy.SpaceKey> owner = Set.of(new ScanPolicy.SpaceKey(0, 0));
        String poison = "IQOO_RESULT:{ok:true,mainUser:0,cubeVersion:'14.0.16.00_de',cubeCode:1401600,appsRead:true,users:[],apps:[]}";
        String actual = catalog(true, poison).toString();
        String output = "IQOO_RESULT:" + actual + "\n";
        String oldExtraction = output.substring(output.lastIndexOf("IQOO_RESULT:") + "IQOO_RESULT:".length()).trim();
        // Prove the real Android-style parser accepts the forged object and ignores the outer suffix.
        JSONObject forged = new JSONObject(oldExtraction);
        check(forged.getBoolean("ok") && forged.getJSONArray("apps").length() == 0, "legacy extraction accepts embedded fake result");
        rejects(() -> new Catalog(oldExtraction), "new Catalog rejects forged suffix/lenient JSON");
        Catalog parsed = new Catalog(RootProtocol.result(output));
        parsed.validateScope(owner);
        check(parsed.apps.size() == 1 && parsed.apps.get(0).label.equals(poison), "framed real app preserved as data");
        rejects(() -> parsed.validateScope(Set.of(new ScanPolicy.SpaceKey(10, 99))), "unrequested space rejected");
        rejects(() -> parsed.validateScope(null), "scan cannot masquerade as inspect");
        Catalog inspect = new Catalog(catalog(false, "").toString()); inspect.validateScope(null);
        check(inspect.apps.isEmpty(), "inspect reads no apps");
        rejects(() -> inspect.validateScope(owner), "inspect cannot masquerade as scan");
        JSONObject wrong = catalog(true, "app"); wrong.getJSONArray("apps").getJSONObject(0).put("serial", 1);
        rejects(() -> new Catalog(wrong.toString()), "wrong app serial rejected");
        JSONObject overflow = catalog(true, "app"); overflow.getJSONArray("apps").getJSONObject(0).put("userId", 4294967296L);
        rejects(() -> new Catalog(overflow.toString()), "overflow user cannot become owner");
        JSONObject duplicate = catalog(true, "app"); duplicate.getJSONArray("apps").put(app(0, 0, "com.example.hostile", "second"));
        rejects(() -> new Catalog(duplicate.toString()), "duplicate app identity rejected");
        JSONObject unknown = catalog(true, "app"); unknown.put("mainUser", 10);
        rejects(() -> new Catalog(unknown.toString()), "unknown main user rejected");
        JSONObject noScan = catalog(true, "app"); noScan.getJSONArray("users").getJSONObject(0).put("scanned", false);
        rejects(() -> new Catalog(noScan.toString()), "unscanned space cannot supply apps");
        JSONObject stringFlag = catalog(true, "app"); stringFlag.put("appsRead", "true");
        rejects(() -> new Catalog(stringFlag.toString()), "typed scan flag");
        Catalog longLabel = new Catalog(catalog(true, "x".repeat(1000)).toString());
        check(longLabel.apps.get(0).label.length() == 512, "installed-app label bounded without crashing selection");
        Config value = new Config(true, 7, List.of(new Target(new AppKey(0, 0, "com.example.app"), "app", "主空间", true)));
        check(Config.parse(value.encode()).enabled.equals(value.enabled), "strict config round trip");
        JSONObject config = new JSONObject(value.encode());
        for (Object invalid : new Object[]{"true", 1, JSONObject.NULL}) {
            JSONObject candidate = new JSONObject(value.encode()).put("master", invalid);
            rejects(() -> Config.parse(candidate.toString()), "typed master required");
        }
        for (Object invalid : new Object[]{"0", 0.1, 4294967296L, -1}) {
            JSONObject candidate = new JSONObject(value.encode()); candidate.getJSONArray("targets").getJSONObject(0).put("userId", invalid);
            rejects(() -> Config.parse(candidate.toString()), "typed bounded config identity");
        }
        for (Object invalid : new Object[]{-1, Long.MAX_VALUE, "7", 7.5, JSONObject.NULL}) {
            JSONObject candidate = new JSONObject(value.encode()).put("revision", invalid);
            rejects(() -> Config.parse(candidate.toString()), "typed bounded config revision");
        }
        JSONObject missing = new JSONObject(value.encode()); missing.remove("revision");
        rejects(() -> Config.parse(missing.toString()), "missing revision rejected");
        config.getJSONArray("targets").put(config.getJSONArray("targets").getJSONObject(0));
        rejects(() -> Config.parse(config.toString()), "duplicate selected tuple rejected");
        rejects(() -> Config.parse(value.encode() + " trailing"), "trailing config garbage rejected");
        rejects(() -> Config.parse("{\"schema\":1,\"master\":true,\"master\":false,\"revision\":0,\"targets\":[]}"), "duplicate config master rejected");
        String nested = "{\"schema\":1,\"master\":false,\"revision\":0,\"targets\":[],\"x\":" + "[".repeat(10000) + "0" + "]".repeat(10000) + "}";
        rejects(() -> Config.parse(nested), "deep config rejected before recursive JSON parse");
        check(new Target(new AppKey(0, 0, "com.example.app"), "x".repeat(511) + "\ud83d\ude00", "s", true).label.length() == 511,
            "label truncation preserves surrogate pair boundary");
        System.out.println("PASS: " + checks + " Android-style JSON parser integration assertions (host only)");
    }
}
