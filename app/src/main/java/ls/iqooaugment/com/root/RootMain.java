package ls.iqooaugment.com.root;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.Process;
import android.os.UserManager;
import android.util.Base64;
import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.AdmissionPolicy;
import ls.iqooaugment.com.core.ScanPolicy;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** One-shot, read-only root helper. It cannot install apps, change settings, or write vendor data. */
public final class RootMain {
    private static int iconBytes;
    public static void main(String[] args) {
        JSONObject output = new JSONObject();
        int exit = 1;
        try {
            if (Process.myUid() != 0) throw new SecurityException("Root 未授权");
            boolean readApps = args.length == 2 && "scan".equals(args[0]);
            Set<ScanPolicy.SpaceKey> selected;
            if (readApps) selected = ScanPolicy.parseSelection(args[1]);
            else if (args.length == 1 && "inspect".equals(args[0])) selected = new HashSet<>();
            else throw new IllegalArgumentException("Unsupported operation or missing space selection");
            if (Looper.myLooper() == null) Looper.prepareMainLooper();
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("systemMain").invoke(null);
            Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
            output = read(context, selected, readApps); exit = 0;
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            try { output.put("ok", false).put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage()); }
            catch (Exception ignored) { }
        }
        System.out.println("IQOO_RESULT:" + output);
        System.out.flush();
        System.exit(exit);
    }
    private static final class Space {
        final int id; final long serial; final ScanPolicy.Kind kind;
        final String title; final boolean unlocked;
        String warning; boolean scanned;
        Space(int id, long serial, ScanPolicy.Kind kind, boolean unlocked, String warning) {
            this.id = id; this.serial = serial; this.kind = kind;
            this.unlocked = unlocked; this.warning = warning;
            title = kind == ScanPolicy.Kind.MAIN ? "主空间" : kind == ScanPolicy.Kind.CLONE ? "分身空间" :
                kind == ScanPolicy.Kind.WORK ? "工作空间" : kind == ScanPolicy.Kind.PRIVATE ? "私密空间" : "其他空间";
        }
        JSONObject json() throws Exception {
            // No user-supplied names or raw OEM metadata are needed to choose a scan scope.
            return new JSONObject().put("id", id).put("serial", serial).put("title", title)
                .put("kind", kind.name()).put("unlocked", unlocked).put("scanned", scanned)
                .put("defaultSelected", ScanPolicy.defaultSelected(kind, unlocked, serial)).put("warning", warning);
        }
    }
    private static JSONObject read(Context context, Set<ScanPolicy.SpaceKey> selected, boolean readApps) throws Exception {
        UserManager um = context.getSystemService(UserManager.class);
        if (um == null) throw new IllegalStateException("UserManager unavailable");
        List<Object> infos = AndroidUsers.users(um);
        if (infos.size() > ScanPolicy.MAX_SPACES) throw new IllegalStateException("空间数量超出读取上限");
        int main = AndroidUsers.mainUser(um);
        List<Space> descriptors = new ArrayList<>();
        Set<ScanPolicy.SpaceKey> available = new HashSet<>();
        for (Object info : infos) {
            int id = ((Number) AndroidUsers.field(info, "id")).intValue();
            if (id < 0) throw new IllegalStateException("空间编号不可核验");
            String type;
            try { type = String.valueOf(AndroidUsers.field(info, "userType")); }
            catch (Exception e) { type = ""; }
            ScanPolicy.Kind kind = ScanPolicy.classify(id == main, type,
                AndroidUsers.flag(info, "isCloneProfile") || AndroidUsers.flag(info, "isClonedProfile"),
                AndroidUsers.flag(info, "isManagedProfile"), AndroidUsers.flag(info, "isPrivateProfile"));
            long serial = -1; boolean unlocked = false; String warning = "";
            try { serial = um.getSerialNumberForUser(AndroidUsers.handle(id)); }
            catch (Exception ignored) { }
            if (serial < 0) warning = "无法核验空间序列号，应用列表未读取";
            else {
                available.add(new ScanPolicy.SpaceKey(id, serial));
                try { unlocked = AndroidUsers.unlocked(um, id); }
                catch (Exception e) { warning = "无法核验空间解锁状态，应用列表未读取"; }
            }
            if (!unlocked && warning.isEmpty()) warning = "空间未解锁或尚未启动，应用列表未读取";
            descriptors.add(new Space(id, serial, kind, unlocked, warning));
        }
        // Reject deleted/recreated profiles before any application enumeration starts.
        if (readApps) ScanPolicy.validateSelection(selected, available);
        JSONArray spaces = new JSONArray(), apps = new JSONArray();
        for (Space space : descriptors) {
            if (readApps && ScanPolicy.mayScan(selected, space.id, space.serial, space.unlocked)) {
                try {
                    Context uc = AndroidUsers.contextFor(context, space.id);
                    if (um.getSerialNumberForUser(AndroidUsers.handle(space.id)) != space.serial
                        || !AndroidUsers.unlocked(um, space.id))
                        throw new IllegalStateException("空间身份或解锁状态已变化，请重新选择");
                    List<JSONObject> found = scanApps(uc, space.id, space.serial, space.title);
                    if (um.getSerialNumberForUser(AndroidUsers.handle(space.id)) != space.serial
                        || !AndroidUsers.unlocked(um, space.id))
                        throw new IllegalStateException("空间身份或解锁状态已变化，此空间结果已丢弃");
                    if (apps.length() + found.size() > 4000) throw new IllegalStateException("应用数量超出读取上限，此空间结果未加入");
                    for (JSONObject app : found) apps.put(app);
                    space.scanned = true;
                } catch (Exception e) { space.warning = "该空间读取不完整：" + rootMessage(e); }
            } else if (space.warning.isEmpty()) {
                space.warning = readApps ? "未选择此空间，应用列表未读取" : "仅已识别空间，应用列表未读取";
            }
            spaces.put(space.json());
        }
        // The only package read during inspect is the exact compatibility target in the main space.
        Context mainContext = AndroidUsers.contextFor(context, main);
        String cubeVersion = "未安装"; long cubeCode = -1;
        try {
            PackageInfo pi = mainContext.getPackageManager().getPackageInfo(Constants.CUBE, 0);
            cubeVersion = pi.versionName; cubeCode = pi.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) { }
        return new JSONObject().put("ok", true).put("mainUser", main)
            .put("cubeVersion", cubeVersion).put("cubeCode", cubeCode).put("appsRead", readApps)
            .put("users", spaces).put("apps", apps);
    }
    private static List<JSONObject> scanApps(Context context, int id, long serial, String space) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.MATCH_DISABLED_COMPONENTS;
        Map<String, ApplicationInfo> launchable = new LinkedHashMap<>();
        Set<String> excluded = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags))
            if (ri.activityInfo != null && ri.activityInfo.applicationInfo != null)
                launchable.put(ri.activityInfo.packageName, ri.activityInfo.applicationInfo);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), flags))
            if (ri.activityInfo != null) excluded.add(ri.activityInfo.packageName);
        for (ResolveInfo ri : pm.queryIntentServices(new Intent("android.view.InputMethod"), flags))
            if (ri.serviceInfo != null) excluded.add(ri.serviceInfo.packageName);
        List<JSONObject> result = new ArrayList<>();
        for (ApplicationInfo ai : launchable.values()) {
            String pkg = ai.packageName;
            if (!AdmissionPolicy.allowedPackage(pkg) || excluded.contains(pkg)
                || (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            boolean eligible = ai.enabled; String reason = eligible ? "" : "应用已停用";
            if (pm.isPackageSuspended(pkg)) { eligible = false; reason = "应用已暂停"; }
            String label;
            try { label = String.valueOf(pm.getApplicationLabel(ai)); }
            catch (Exception e) { label = pkg; }
            String icon = "";
            if (iconBytes < 2_500_000) try {
                Drawable drawable = pm.getApplicationIcon(ai);
                Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, 64, 64); drawable.draw(new Canvas(bitmap));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle();
                if (bytes.size() <= 32_768) {
                    icon = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP); iconBytes += icon.length();
                }
            } catch (Exception ignored) { /* A failed icon is not a failed application record. */ }
            result.add(new JSONObject().put("userId", id).put("serial", serial).put("pkg", pkg)
                .put("label", label).put("space", space).put("icon", icon)
                .put("eligible", eligible).put("reason", reason));
        }
        return result;
    }
    private static String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }
}
