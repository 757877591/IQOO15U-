import ls.iqooaugment.com.core.RootProtocol;

/** Framing tests cover installed-app label injection without running su or Android. */
public final class RootProtocolTests {
    static int checks;
    static void check(boolean condition, String name) { checks++; if (!condition) throw new AssertionError(name); }
    static void rejects(String input, String name) {
        boolean rejected = false;
        try { RootProtocol.result(input); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, name);
    }
    public static void main(String[] args) {
        String real = "{\"ok\":true,\"label\":\"IQOO_RESULT:{ok:true,mainUser:0,apps:[]}\"}";
        check(RootProtocol.result("IQOO_RESULT:" + real + "\n").equals(real), "label marker remains data");
        check(RootProtocol.result("su diagnostic\nIQOO_RESULT:" + real + "\n").equals(real), "leading logs allowed");
        check(RootProtocol.result("IQOO_RESULT:{}\r\ntrailing diagnostic\n").equals("{}"), "CRLF and trailing logs");
        check(RootProtocol.result("IQOO_RESULT:{}").equals("{}"), "final line without newline");
        check(RootProtocol.result("embedded IQOO_RESULT:{bad}\nIQOO_RESULT:{}\n").equals("{}"), "embedded marker ignored");
        rejects(null, "null output");
        rejects("", "missing output");
        rejects("ordinary IQOO_RESULT:{}\n", "marker must start line");
        rejects("IQOO_RESULT:{}\nIQOO_RESULT:{}\n", "duplicate frame rejected");
        rejects("IQOO_RESULT:{ok:true}\n", "lenient forged object rejected");
        rejects("IQOO_RESULT:{'ok':true}\n", "single-quoted forged object rejected");
        rejects("IQOO_RESULT:{}suffix\n", "outer JSON suffix rejected");
        rejects("IQOO_RESULT:{\"ok\":true,\"ok\":false}\n", "duplicate claims rejected");
        rejects("IQOO_RESULT:" + "[".repeat(9) + "0" + "]".repeat(9), "deep result rejected");
        rejects("x".repeat(RootProtocol.MAX_OUTPUT + 1), "oversized output rejected");
        for (int i = 0; i < 1000; i++) {
            String result = "{\"label\":\"prefix IQOO_RESULT:{uid:" + i + "} tail\"}";
            check(RootProtocol.result("IQOO_RESULT:" + result + "\n").equals(result), "injected label cannot replace whole frame");
        }
        System.out.println("PASS: " + checks + " Root output framing assertions (no device scan claim)");
    }
}
