package ls.iqooaugment.com.hook;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.UserManager;
import ls.iqooaugment.com.core.AdmissionPolicy;
import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.root.AndroidUsers;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Takes an actual foreground-task identity, never am.getCurrentUser(), daemon UID, or package UID guessed in owner. */
final class ForegroundResolver {
    private final Context context;
    private final BooleanSupplier observing;
    ForegroundResolver(Context context, BooleanSupplier observing) { this.context = context; this.observing = observing; }
    AppKey resolve(String expectedPackage) throws Exception {
        if (!observing.getAsBoolean()) return null;
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager lock = context.getSystemService(KeyguardManager.class);
        if (power == null || !power.isInteractive() || lock == null || lock.isKeyguardLocked()) return null;
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) throw new IllegalStateException("ActivityManager unavailable");
        if (!observing.getAsBoolean()) return null;
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!observing.getAsBoolean()) return null;
        if (tasks == null || tasks.isEmpty()) return null;
        ActivityManager.RunningTaskInfo top = tasks.get(0);
        if (top.topActivity == null) return null;
        String pkg = top.topActivity.getPackageName();
        if (expectedPackage != null && !expectedPackage.equals(pkg)) return null;
        if (!AppKey.validPackage(pkg)) return null;
        if (!Boolean.TRUE.equals(AndroidUsers.field(top, "isFocused"))
            || !Boolean.TRUE.equals(AndroidUsers.field(top, "isVisible"))
            || Boolean.TRUE.equals(AndroidUsers.field(top, "isSleeping"))) return null;
        if (((Number) AndroidUsers.field(top, "displayId")).intValue() != 0) return null;
        int user = ((Number) AndroidUsers.field(top, "userId")).intValue();
        ActivityInfo activity = (ActivityInfo) AndroidUsers.field(top, "topActivityInfo");
        if (activity == null || activity.applicationInfo == null
            || activity.applicationInfo.uid / 100000 != user)
            throw new IllegalStateException("Task userId 与前台 Activity UID 不一致，拒绝跨空间推断");
        UserManager um = context.getSystemService(UserManager.class);
        if (um == null || !AndroidUsers.unlocked(um, user)) return null;
        long serial = um.getSerialNumberForUser(AndroidUsers.handle(user));
        if (serial < 0) throw new IllegalStateException("前台空间序列号不可读");
        return new AppKey(user, serial, pkg);
    }
    boolean eligible(AppKey key) throws Exception {
        if (!observing.getAsBoolean() || key == null || !AdmissionPolicy.allowedPackage(key.packageName)) return false;
        Context uc = AndroidUsers.contextFor(context, key.userId);
        PackageManager pm = uc.getPackageManager();
        ApplicationInfo ai = pm.getApplicationInfo(key.packageName, 0);
        if (!ai.enabled || (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            || pm.isPackageSuspended(key.packageName) || pm.getLaunchIntentForPackage(key.packageName) == null) return false;
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            if (ri.activityInfo != null && key.packageName.equals(ri.activityInfo.packageName)) return false;
        for (ResolveInfo ri : pm.queryIntentServices(new Intent("android.view.InputMethod"), 0))
            if (ri.serviceInfo != null && key.packageName.equals(ri.serviceInfo.packageName)) return false;
        return true;
    }
}
