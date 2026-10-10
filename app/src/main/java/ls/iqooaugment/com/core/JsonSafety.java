package ls.iqooaugment.com.core;

import java.util.HashSet;
import java.util.Set;

/** Bounded strict syntax before Android's permissive, recursive JSON parser. */
public final class JsonSafety {
    private JsonSafety() { }

    /** Reject deep/oversized, ambiguous or trailing input before allocating a JSON tree. */
    public static void requireDocument(String raw, int maxChars, int maxDepth) {
        if (raw == null || raw.isEmpty() || maxChars < 1 || raw.length() > maxChars
                || maxDepth < 1 || maxDepth > 32)
            throw new IllegalArgumentException("Invalid JSON size or depth limit");
        Reader reader = new Reader(raw, maxDepth);
        reader.value(0);
        reader.whitespace();
        if (reader.position != raw.length()) throw reader.invalid();
    }

    /** Identity fields never accept strings, floating point, rounding or int truncation. */
    public static long integer(Object value, String name, long minimum, long maximum) {
        if (!(value instanceof Integer || value instanceof Long))
            throw new IllegalArgumentException("Invalid integer: " + name);
        long result = ((Number) value).longValue();
        if (result < minimum || result > maximum)
            throw new IllegalArgumentException("Out-of-range integer: " + name);
        return result;
    }

    public static boolean bool(Object value, String name) {
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid boolean: " + name);
        return (Boolean) value;
    }

    public static String string(Object value, String name, int maxChars) {
        if (!(value instanceof String) || ((String) value).length() > maxChars)
            throw new IllegalArgumentException("Invalid string: " + name);
        return (String) value;
    }

    private static final class Reader {
        final String raw;
        final int maxDepth;
        int position;
        Reader(String raw, int maxDepth) { this.raw = raw; this.maxDepth = maxDepth; }
        IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid JSON document"); }
        void whitespace() {
            while (position < raw.length()) {
                char c = raw.charAt(position);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
                position++;
            }
        }
        boolean take(char wanted) {
            if (position < raw.length() && raw.charAt(position) == wanted) { position++; return true; }
            return false;
        }
        void expect(char wanted) { if (!take(wanted)) throw invalid(); }
        void value(int depth) {
            whitespace();
            if (position == raw.length()) throw invalid();
            char c = raw.charAt(position);
            if (c == '{' || c == '[') {
                if (depth >= maxDepth) throw invalid();
                position++;
                if (c == '{') object(depth + 1); else array(depth + 1);
            } else if (c == '"') stringValue();
            else if (c == 't') literal("true");
            else if (c == 'f') literal("false");
            else if (c == 'n') literal("null");
            else number();
        }
        void object(int depth) {
            Set<String> names = new HashSet<>();
            whitespace();
            if (take('}')) return;
            do {
                whitespace();
                String name = stringValue();
                if (!names.add(name)) throw invalid();
                whitespace(); expect(':'); value(depth); whitespace();
                if (take('}')) return;
                expect(',');
            } while (true);
        }
        void array(int depth) {
            whitespace();
            if (take(']')) return;
            do {
                value(depth); whitespace();
                if (take(']')) return;
                expect(',');
            } while (true);
        }
        String stringValue() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < raw.length()) {
                char c = raw.charAt(position++);
                if (c == '"') return value.toString();
                if (c < 0x20) throw invalid();
                if (c == '\\') {
                    if (position == raw.length()) throw invalid();
                    c = raw.charAt(position++);
                    switch (c) {
                        case '"': case '\\': case '/': break;
                        case 'b': c = '\b'; break;
                        case 'f': c = '\f'; break;
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'u':
                            if (raw.length() - position < 4) throw invalid();
                            int decoded = 0;
                            for (int i = 0; i < 4; i++) {
                                char hex = raw.charAt(position++);
                                int digit = hex >= '0' && hex <= '9' ? hex - '0'
                                    : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10
                                    : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
                                if (digit < 0) throw invalid();
                                decoded = (decoded << 4) | digit;
                            }
                            c = (char) decoded;
                            break;
                        default: throw invalid();
                    }
                }
                value.append(c);
            }
            throw invalid();
        }
        void literal(String value) {
            if (!raw.startsWith(value, position)) throw invalid();
            position += value.length();
        }
        boolean digit() {
            return position < raw.length() && raw.charAt(position) >= '0' && raw.charAt(position) <= '9';
        }
        void digits() {
            if (!digit()) throw invalid();
            do { position++; } while (digit());
        }
        void number() {
            take('-');
            if (!take('0')) digits();
            if (take('.')) digits();
            if (take('e') || take('E')) { if (!take('+')) take('-'); digits(); }
        }
    }
}
