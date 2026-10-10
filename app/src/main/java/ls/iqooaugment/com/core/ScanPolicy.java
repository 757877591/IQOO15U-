package ls.iqooaugment.com.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure privacy boundary: application enumeration always needs an exact, explicit space selection. */
public final class ScanPolicy {
    public static final int MAX_SPACES = 64;
    private static final Pattern SPACE_KEY = Pattern.compile("(?:0|[1-9][0-9]*):(?:0|[1-9][0-9]*)");
    public enum Kind { MAIN, CLONE, WORK, PRIVATE, OTHER }
    private ScanPolicy() { }

    public static final class SpaceKey {
        public final int userId;
        public final long serial;
        public SpaceKey(int userId, long serial) {
            if (userId < 0 || serial < 0) throw new IllegalArgumentException("Invalid space identity");
            this.userId = userId; this.serial = serial;
        }
        public String encode() { return userId + ":" + serial; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof SpaceKey)) return false;
            SpaceKey other = (SpaceKey) value;
            return userId == other.userId && serial == other.serial;
        }
        @Override public int hashCode() { return Objects.hash(userId, serial); }
    }

    /** A name, a commonly used clone ID, or an unrecognized OEM type never proves a clone. */
    public static Kind classify(boolean main, String type, boolean cloneFlag, boolean managedFlag, boolean privateFlag) {
        if (privateFlag || "android.os.usertype.profile.PRIVATE".equals(type)) return Kind.PRIVATE;
        if (managedFlag || "android.os.usertype.profile.MANAGED".equals(type)) return Kind.WORK;
        if (main) return Kind.MAIN;
        if (cloneFlag || "android.os.usertype.profile.CLONE".equals(type)) return Kind.CLONE;
        return Kind.OTHER;
    }
    public static boolean defaultSelected(Kind kind, boolean unlocked, long serial) {
        return unlocked && serial >= 0 && (kind == Kind.MAIN || kind == Kind.CLONE);
    }
    public static Set<SpaceKey> parseSelection(String value) {
        if (value == null || value.isEmpty() || value.length() > 4096)
            throw new IllegalArgumentException("Missing or oversized space selection");
        String[] parts = value.split(",", -1);
        if (parts.length > MAX_SPACES) throw new IllegalArgumentException("Too many selected spaces");
        Set<SpaceKey> result = new LinkedHashSet<>();
        Set<Integer> users = new LinkedHashSet<>();
        for (String part : parts) {
            if (!SPACE_KEY.matcher(part).matches()) throw new IllegalArgumentException("Malformed space selection");
            String[] pair = part.split(":", -1);
            SpaceKey key = new SpaceKey(Integer.parseInt(pair[0]), Long.parseLong(pair[1]));
            if (!users.add(key.userId)) throw new IllegalArgumentException("Duplicate selected user");
            result.add(key);
        }
        return result;
    }
    public static String encodeSelection(Collection<SpaceKey> selection) {
        if (selection == null || selection.isEmpty() || selection.size() > MAX_SPACES)
            throw new IllegalArgumentException("Select at least one space");
        StringBuilder out = new StringBuilder();
        for (SpaceKey key : selection) {
            if (key == null) throw new IllegalArgumentException("Missing space identity");
            if (out.length() > 0) out.append(',');
            out.append(key.encode());
        }
        String encoded = out.toString();
        parseSelection(encoded); // Reject duplicates and malformed callers before starting Root.
        return encoded;
    }
    public static void validateSelection(Set<SpaceKey> requested, Set<SpaceKey> available) {
        if (requested == null || requested.isEmpty() || available == null || !available.containsAll(requested))
            throw new IllegalArgumentException("空间已变化，请重新选择后读取");
    }
    public static boolean mayScan(Set<SpaceKey> selected, int userId, long serial, boolean unlocked) {
        return unlocked && userId >= 0 && serial >= 0 && selected != null
            && selected.contains(new SpaceKey(userId, serial));
    }
}
