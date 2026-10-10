package ls.iqooaugment.com.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure policy. Never removes a native entry or broadens a list outside a foreground call. */
public final class AdmissionPolicy {
    private static final Set<String> DENY = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "android", "com.android.systemui", "com.android.settings", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.bbk.lockscreen3",
        "com.vivo.gamecube", "com.vivo.gamewatch", "com.vivo.gamespace", "com.vivo.game",
        "ls.iqooaugment.com")));
    private AdmissionPolicy() { }
    public static boolean allowedPackage(String pkg) { return AppKey.validPackage(pkg) && !DENY.contains(pkg); }
    public static List<String> extend(List<String> nativeList, AppKey foreground,
            Set<AppKey> selected, boolean master, boolean trustedEnvironment) {
        if (nativeList == null || foreground == null || !master || !trustedEnvironment
                || !allowedPackage(foreground.packageName) || !selected.contains(foreground)
                || nativeList.contains(foreground.packageName)) return nativeList;
        ArrayList<String> result = new ArrayList<>(nativeList);
        result.add(foreground.packageName);
        return result;
    }
}
