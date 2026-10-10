package ls.iqooaugment.com.core;
/** The only shell argument quoting helper; app labels never enter a shell command. */
public final class ShellQuote {
    private ShellQuote() { }
    public static String of(String value) {
        if (value == null || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid argument");
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
