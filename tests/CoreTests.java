import ls.iqooaugment.com.core.*;
import java.util.*;

/** Plain JDK tests: policy/data identities only; not Android or device integration tests. */
public final class CoreTests {
    static int checks;
    static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    static void rejects(Runnable block, String name) {
        boolean threw = false;
        try { block.run(); } catch (IllegalArgumentException expected) { threw = true; }
        check(threw, name);
    }
    public static void main(String[] args) {
        AppKey owner = new AppKey(0, 0, "com.example.player");
        AppKey clone = new AppKey(27, 104, "com.example.player");
        AppKey recreated = new AppKey(27, 105, "com.example.player");
        List<String> original = Collections.unmodifiableList(Arrays.asList("com.example.nativegame"));
        Set<AppKey> ownerOnly = Set.of(owner), cloneOnly = Set.of(clone);
        check(owner.equals(AppKey.decode(owner.encode())), "identity round trip");
        check(clone.equals(AppKey.decode(clone.encode())), "clone identity round trip");
        check(!owner.equals(clone), "same package different users");
        check(!clone.equals(recreated), "recreated profile not equal");
        check(new HashSet<>(List.of(owner, clone, recreated)).size()==3, "independent hash identity");
        check(AdmissionPolicy.extend(original, owner, ownerOnly,true,true).contains(owner.packageName), "owner selected");
        check(AdmissionPolicy.extend(original, clone, ownerOnly,true,true)==original, "owner must not unlock clone");
        check(AdmissionPolicy.extend(original, owner, cloneOnly,true,true)==original, "clone must not unlock owner");
        check(AdmissionPolicy.extend(original, clone, cloneOnly,true,true).contains(clone.packageName), "clone selected");
        check(AdmissionPolicy.extend(original, recreated, cloneOnly,true,true)==original, "old serial not inherited");
        check(AdmissionPolicy.extend(original, owner, ownerOnly,false,true)==original, "master off");
        check(AdmissionPolicy.extend(original, owner, ownerOnly,true,false)==original, "untrusted environment");
        check(AdmissionPolicy.extend(original, null, ownerOnly,true,true)==original, "unknown foreground");
        check(AdmissionPolicy.extend(null, owner, ownerOnly,true,true)==null, "uninitialized native list");
        check(AdmissionPolicy.extend(original, owner, Set.of(),true,true)==original, "no targets");
        check(original.equals(List.of("com.example.nativegame")), "no in-place native mutation");
        List<String> result=AdmissionPolicy.extend(original,owner,ownerOnly,true,true);
        check(result.size()==2 && result.get(0).equals(original.get(0)), "native order retained");
        check(AdmissionPolicy.extend(result,owner,ownerOnly,true,true)==result, "no duplicates");
        check(AdmissionPolicy.extend(result,clone,ownerOnly,true,true)==result, "native support never revoked");
        check(AdmissionPolicy.extend(Collections.emptyList(),owner,ownerOnly,true,true).equals(List.of(owner.packageName)), "empty initialized native list allowed");
        check(!AdmissionPolicy.allowedPackage("com.android.systemui"), "SystemUI protected");
        check(!AdmissionPolicy.allowedPackage("com.vivo.gamecube"), "host protected");
        check(!AdmissionPolicy.allowedPackage("ls.iqooaugment.com"), "self protected");
        check(!AdmissionPolicy.allowedPackage("com.android.permissioncontroller"), "permissions protected");
        rejects(()->new AppKey(-1,0,"com.example.app"),"negative user");
        rejects(()->new AppKey(0,-1,"com.example.app"),"missing serial");
        rejects(()->AppKey.decode("0:0:com.example.app:extra"),"extra identity field");
        rejects(()->AppKey.decode("bad:0:com.example.app"),"malformed user");
        rejects(()->AppKey.decode(null),"missing identity");
        rejects(()->new AppKey(0,0,"com.example;reboot"),"package shell injection");
        rejects(()->new AppKey(0,0,"com.example.app\n"),"package newline");
        rejects(()->new AppKey(0,0,"com..app"),"empty package segment");
        check(ShellQuote.of("/data/app/base.apk").equals("'/data/app/base.apk'"),"normal shell quote");
        check(ShellQuote.of("a'b").equals("'a'\\''b'"),"apostrophe shell quote");
        check(ShellQuote.of("$(reboot);x").equals("'$(reboot);x'"),"metacharacters quoted");
        rejects(()->ShellQuote.of("x\u0000y"),"NUL shell input");
        check(UiMetrics.DETAIL_CONTENT_GAP_DP==8,"detail gap source constant");
        check(UiMetrics.SECTION_TO_CARD_DP==16,"section-card gap source constant");
        check(UiMetrics.CONTENT_SIDE_DP==16,"side inset source constant");
        check(UiMetrics.HOME_TITLE_TO_SEARCH_DP==22&&UiMetrics.HOME_SEARCH_TO_LIST_DP==12,"home spacing source constants");
        check(UiMetrics.PATH_TEXT_SP==13.225f,"breadcrumb scalable size source constant");
        check(UiMetrics.SWITCH_TRACK_W_DP==46&&UiMetrics.SWITCH_TRACK_H_DP==28&&UiMetrics.SWITCH_THUMB_DP==22,"switch source constants");
        check(UiMetrics.TOUCH_DP>=48&&UiMetrics.FEATURE_MIN_DP>=44,"touch source constants");
        int directed=checks;
        // Deterministic invariant sweep; this does not simulate OEM task callbacks.
        for(int user=1;user<=100;user++) for(int serial=1;serial<=20;serial++) {
            AppKey instance=new AppKey(user,serial,"com.example.player");
            check(AdmissionPolicy.extend(original,instance,ownerOnly,true,true)==original,"randomized user isolation");
            check(AdmissionPolicy.extend(original,instance,Set.of(instance),true,true).size()==2,"exact tuple admitted");
            check(AdmissionPolicy.extend(original,new AppKey(user,serial+1,instance.packageName),Set.of(instance),true,true)==original,"serial lifecycle isolation");
        }
        System.out.println("PASS: "+directed+" directed assertions + "+(checks-directed)+" deterministic invariant assertions = "+checks);
    }
}
