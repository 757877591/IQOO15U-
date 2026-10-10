import ls.iqooaugment.com.core.ScanPolicy;
import ls.iqooaugment.com.core.ScanPolicy.Kind;
import ls.iqooaugment.com.core.ScanPolicy.SpaceKey;
import java.util.*;

/** Host-JDK tests for least-privilege scan selection, not Android/device tests. */
public final class ScanPolicyTests {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    private static void rejects(Runnable operation, String name) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, name);
    }
    public static void main(String[] args) {
        check(ScanPolicy.classify(true, "", false, false, false)==Kind.MAIN, "verified main");
        check(ScanPolicy.classify(false, "android.os.usertype.profile.CLONE", false, false, false)==Kind.CLONE, "platform clone type");
        check(ScanPolicy.classify(false, "", true, false, false)==Kind.CLONE, "verified OEM clone flag");
        check(ScanPolicy.classify(false, "vendor.made.up.CLONE", false, false, false)==Kind.OTHER, "suffix does not prove clone");
        check(ScanPolicy.classify(false, "android.os.usertype.profile.MANAGED", true, false, false)==Kind.WORK, "managed overrides clone flag");
        check(ScanPolicy.classify(false, "android.os.usertype.profile.PRIVATE", true, false, false)==Kind.PRIVATE, "private overrides clone flag");
        check(ScanPolicy.classify(true, "", true, true, false)==Kind.WORK, "managed flag overrides main and clone");
        check(ScanPolicy.classify(true, "", true, false, true)==Kind.PRIVATE, "private flag overrides main and clone");
        check(ScanPolicy.defaultSelected(Kind.MAIN, true, 0), "main default");
        check(ScanPolicy.defaultSelected(Kind.CLONE, true, 104), "verified clone default");
        for (Kind kind : Kind.values()) {
            check(!ScanPolicy.defaultSelected(kind, false, 0), "locked never default");
            check(!ScanPolicy.defaultSelected(kind, true, -1), "unknown serial never default");
        }
        for (Kind kind : List.of(Kind.WORK, Kind.PRIVATE, Kind.OTHER))
            check(!ScanPolicy.defaultSelected(kind, true, 104), "sensitive or unknown space never default");
        Set<SpaceKey> requested=ScanPolicy.parseSelection("0:0,27:104");
        check(ScanPolicy.encodeSelection(requested).equals("0:0,27:104"), "identity selection round trip");
        check(ScanPolicy.mayScan(requested, 0, 0, true), "explicit main allowed");
        check(ScanPolicy.mayScan(requested, 27, 104, true), "explicit clone allowed");
        check(!ScanPolicy.mayScan(requested, 27, 105, true), "recreated selected profile denied");
        check(!ScanPolicy.mayScan(requested, 0, 1, true), "recreated selected main denied");
        check(!ScanPolicy.mayScan(requested, 27, 104, false), "locked selected space denied");
        check(!ScanPolicy.mayScan(requested, 10, 10, true), "unselected unlocked workspace denied");
        check(!ScanPolicy.mayScan(Collections.emptySet(), 0, 0, true), "device inspect reads no main apps");
        check(!ScanPolicy.mayScan(null, 0, 0, true), "missing selection never defaults to all");
        check(!ScanPolicy.mayScan(requested, -1, 0, true), "unknown ID denied");
        check(!ScanPolicy.mayScan(requested, 0, -1, true), "unknown serial denied");
        ScanPolicy.validateSelection(requested, requested);check(true, "matching selection accepted");
        rejects(()->ScanPolicy.validateSelection(requested, Set.of(new SpaceKey(0,0),new SpaceKey(27,105))), "changed profile invalidates entire selection before scan");
        rejects(()->ScanPolicy.validateSelection(Collections.emptySet(), requested), "empty selection denied");
        for (String value : new String[]{"", "all", "0", "0:0,", "-1:0", "0:-1", "0:0,0:0", "0:0,0:1", "00:0", "0:00", "0:0;reboot", "0:0\n", "2147483648:0", "0:9223372036854775808"})
            rejects(()->ScanPolicy.parseSelection(value), "invalid or injected selection: "+value);
        rejects(()->ScanPolicy.parseSelection(null), "null selection denied");
        rejects(()->ScanPolicy.encodeSelection(List.of()), "empty encoded selection denied");
        rejects(()->ScanPolicy.encodeSelection(List.of(new SpaceKey(0,0),new SpaceKey(0,1))), "duplicate user cannot carry two serials");
        List<SpaceKey> tooMany=new ArrayList<>();for(int i=0;i<=ScanPolicy.MAX_SPACES;i++)tooMany.add(new SpaceKey(i,i));
        rejects(()->ScanPolicy.encodeSelection(tooMany), "oversized selection denied");
        for(int id=1;id<=100;id++)for(long serial=1;serial<=20;serial++){
            check(!ScanPolicy.mayScan(Set.of(new SpaceKey(0,0)),id,serial,true),"unselected identity isolation");
            check(!ScanPolicy.mayScan(Set.of(new SpaceKey(id,serial)),id,serial+1,true),"serial lifecycle isolation");
        }
        System.out.println("PASS: "+checks+" scan privacy policy assertions");
    }
}
