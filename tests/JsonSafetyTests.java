import ls.iqooaugment.com.core.JsonSafety;

/** Host tests for bounded strict JSON syntax and non-coercing identity fields. */
public final class JsonSafetyTests {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++; if (!condition) throw new AssertionError(name);
    }
    private static void rejects(Runnable operation, String name) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, name);
    }
    private static void document(String input) { JsonSafety.requireDocument(input, 24_000, 8); }
    public static void main(String[] args) {
        for (String input : new String[]{"{}", "[]", "null", "true", "false", "0", "-1", "1234567890",
                "0.01", "1e2", "-0.2E-2", "1e+3", "\"你好\"", "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\"",
                " {\"a\":[0,{\"b\":false}],\"c\":\"[{}]\"} \n", "{\"a\":1,\"A\":2}"}) {
            document(input); check(true, "valid strict JSON");
        }
        for (String input : new String[]{"", " ", "{", "[", "{a:1}", "{'a':1}", "{\"a\":1,}", "[1,]",
                "[1,,2]", "{} garbage", "{}{}", "//comment\n{}", "/*x*/{}", "[+1]", "[01]", "[0x10]",
                "[NaN]", "[Infinity]", "[.1]", "[1.]", "[1e]", "[1e+-1]", "[--1]", "[TRUE]",
                "[falsee]", "[nul]", "{\"a\"=1}", "{\"a\":1;\"b\":2}", "\"\\x00\"", "\"\\u00z0\"",
                "\"\\u01\"", "\"\n\"", "\"unterminated", "{\"a\":0,\"a\":1}",
                "{\"a\":0,\"\\u0061\":1}", "{\"a\":{\"b\":0,\"b\":1}}"})
            rejects(() -> document(input), "malformed or ambiguous JSON rejected");
        rejects(() -> document(null), "null document");
        rejects(() -> document(" ".repeat(24_001)), "oversized status");
        JsonSafety.requireDocument("[".repeat(8) + "0" + "]".repeat(8), 24_000, 8);
        check(true, "depth at limit accepted");
        rejects(() -> document("[".repeat(9) + "0" + "]".repeat(9)), "excess depth rejected");
        rejects(() -> document("[".repeat(10_000) + "0" + "]".repeat(10_000)), "deep input rejected before stack overflow");
        rejects(() -> JsonSafety.requireDocument("{}", 2, 0), "invalid minimum depth");
        rejects(() -> JsonSafety.requireDocument("{}", 2, 10000), "unsafe caller-selected depth");
        check(JsonSafety.integer(0, "userId", 0, Integer.MAX_VALUE) == 0, "integer identity accepted");
        check(JsonSafety.integer(2147483647L, "userId", 0, Integer.MAX_VALUE) == Integer.MAX_VALUE, "exact bounded long");
        check(JsonSafety.integer(Long.MAX_VALUE, "serial", 0, Long.MAX_VALUE) == Long.MAX_VALUE, "exact serial survives");
        for (Object input : new Object[]{-1, 4294967296L, 0.0, "0", "0.0", true, new Object()})
            rejects(() -> JsonSafety.integer(input, "userId", 0, Integer.MAX_VALUE), "coercing or wrapping user identity rejected");
        rejects(() -> JsonSafety.integer(null, "revision", 0, Long.MAX_VALUE - 1), "missing revision");
        rejects(() -> JsonSafety.integer(Long.MAX_VALUE, "revision", 0, Long.MAX_VALUE - 1), "terminal revision rejected");
        check(JsonSafety.bool(true, "master") && !JsonSafety.bool(false, "master"), "typed booleans");
        for (Object input : new Object[]{"true", "false", 1, 0, new Object()})
            rejects(() -> JsonSafety.bool(input, "master"), "coercing boolean rejected");
        rejects(() -> JsonSafety.bool(null, "enabled"), "missing enable rejected");
        check(JsonSafety.string("x", "pkg", 1).equals("x"), "bounded typed string");
        rejects(() -> JsonSafety.string("xx", "pkg", 1), "oversized string");
        rejects(() -> JsonSafety.string(1, "pkg", 1), "coercing string rejected");
        rejects(() -> JsonSafety.string(null, "pkg", 1), "missing string rejected");
        for (int i = 0; i < 1000; i++) {
            String input = "{\"userId\":" + i + ",\"serial\":" + (10000000000L + i) + ",\"enabled\":true}";
            document(input); check(true, "valid identity document sweep");
        }
        System.out.println("PASS: " + checks + " strict JSON/depth/type assertions (no Android IPC claim)");
    }
}
