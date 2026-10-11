import ls.iqooaugment.com.core.UpdatePolicy;

/** Adversarial URL and transfer-boundary tests: feed trust must not become arbitrary downloads. */
public final class UpdatePolicyTests {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    private static void reject(Runnable operation) {
        boolean rejected=false; try { operation.run(); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"unsafe input rejected");
    }
    public static void main(String[] args) {
        String good="https://github.com/liligit1815/lispacerelease/releases/download/iqoo-shoulder-v0.1.0-alpha11/IQOOShoulderExtension-v0.1.0-alpha11-release.apk";
        check(UpdatePolicy.apkUrl(good).toString().equals(good),"expected release accepted");
        for(String bad:new String[]{good.replace("https:","http:"),good.replace("github.com","github.com.evil.test"),
            good.replace("github.com","user@github.com"),good.replace("github.com","github.com:444"),
            good.replace("lispacerelease","other"),good+"?redirect=evil",good+"#fragment",good.replace("download/","download/../"),
            good.replace("download/","download/%2e%2e/"),good.replace("download/","download/one/two/"),good.replace(".apk",".zip")})
            reject(()->UpdatePolicy.apkUrl(bad));
        check(UpdatePolicy.redirect("https://release-assets.githubusercontent.com/github-production-release-asset/file?token=x").getHost().equals("release-assets.githubusercontent.com"),"official signed redirect accepted");
        for(String bad:new String[]{"http://release-assets.githubusercontent.com/file","https://githubusercontent.com.evil.test/file",
            "https://evilgithubusercontent.com/file","https://example.com/file","https://github.com/other/repo","https://127.0.0.1/file",
            "https://user@release-assets.githubusercontent.com/file"})reject(()->UpdatePolicy.redirect(bad));
        check(UpdatePolicy.digest("A".repeat(64)).equals("a".repeat(64)),"digest normalized");
        reject(()->UpdatePolicy.digest("a".repeat(63)));reject(()->UpdatePolicy.digest("g".repeat(64)));
        check(UpdatePolicy.checkedLength(9,1,10)==10,"exact size allowed");
        reject(()->UpdatePolicy.checkedLength(10,1,10));reject(()->UpdatePolicy.checkedLength(0,1,0));
        reject(()->UpdatePolicy.checkedLength(0,1,UpdatePolicy.MAX_APK_BYTES+1));
        reject(()->UpdatePolicy.checkedLength(Long.MAX_VALUE,1,10));
        long midnight=1_797_004_800_000L;
        check(UpdatePolicy.shouldAutomaticallyCheck(midnight,0),"first foreground check allowed");
        check(!UpdatePolicy.shouldAutomaticallyCheck(midnight+120_000,midnight),"midnight crossing does not retry within 24 hours");
        check(!UpdatePolicy.shouldAutomaticallyCheck(midnight+86_399_999,midnight),"24 hour boundary held");
        check(UpdatePolicy.shouldAutomaticallyCheck(midnight+86_400_000,midnight),"24 hour boundary allowed");
        check(!UpdatePolicy.shouldAutomaticallyCheck(midnight-1,midnight),"clock rollback cannot trigger an extra attempt");
        System.out.println("PASS: "+checks+" release URL, redirect and download-boundary checks.");
    }
}
