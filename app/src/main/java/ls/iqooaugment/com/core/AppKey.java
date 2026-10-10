package ls.iqooaugment.com.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** A package is not an identity: user ID + creation serial prevent cross-profile leaks. */
public final class AppKey {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    public final int userId;
    public final long serial;
    public final String packageName;

    public AppKey(int userId, long serial, String packageName) {
        if (userId < 0 || serial < 0 || !validPackage(packageName))
            throw new IllegalArgumentException("Invalid application identity");
        this.userId = userId; this.serial = serial; this.packageName = packageName;
    }
    public static boolean validPackage(String value) {
        return value != null && value.length() <= 255 && PACKAGE.matcher(value).matches();
    }
    public String encode() { return userId + ":" + serial + ":" + packageName; }
    public static AppKey decode(String value) {
        if (value == null) throw new IllegalArgumentException("Missing identity");
        String[] parts = value.split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Malformed identity");
        return new AppKey(Integer.parseInt(parts[0]), Long.parseLong(parts[1]), parts[2]);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof AppKey)) return false;
        AppKey key = (AppKey) other;
        return userId == key.userId && serial == key.serial && packageName.equals(key.packageName);
    }
    @Override public int hashCode() { return Objects.hash(userId, serial, packageName); }
    @Override public String toString() { return encode(); }
}
