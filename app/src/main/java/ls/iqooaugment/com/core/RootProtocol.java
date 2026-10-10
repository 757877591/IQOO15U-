package ls.iqooaugment.com.core;

/** Read one complete framed result; app-controlled labels cannot supply a frame. */
public final class RootProtocol {
    public static final int MAX_OUTPUT = 8 * 1024 * 1024;
    private static final String PREFIX = "IQOO_RESULT:";
    private RootProtocol() { }

    public static String result(String output) {
        if (output == null || output.length() > MAX_OUTPUT)
            throw new IllegalArgumentException("Invalid Root output size");
        String result = null;
        int start = 0;
        while (start < output.length()) {
            int end = output.indexOf('\n', start);
            if (end < 0) end = output.length();
            int contentEnd = end > start && output.charAt(end - 1) == '\r' ? end - 1 : end;
            if (output.startsWith(PREFIX, start)) {
                if (result != null) throw new IllegalArgumentException("Ambiguous Root result");
                result = output.substring(start + PREFIX.length(), contentEnd);
            }
            start = end + 1;
        }
        if (result == null) throw new IllegalArgumentException("Missing Root result");
        JsonSafety.requireDocument(result, MAX_OUTPUT, 8);
        return result;
    }
}
