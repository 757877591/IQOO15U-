package ls.iqooaugment.com.root;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import java.lang.reflect.Method;
import java.util.List;

/** Hidden APIs stay behind reflection. Failure never silently turns a clone into the main user. */
public final class AndroidUsers {
    private AndroidUsers() { }
    public static UserHandle handle(int userId) throws Exception {
        return (UserHandle) UserHandle.class.getMethod("of", int.class).invoke(null, userId);
    }
    public static int id(UserHandle handle) throws Exception {
        return (Integer) UserHandle.class.getMethod("getIdentifier").invoke(handle);
    }
    public static Context contextFor(Context context, int userId) throws Exception {
        return (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class)
            .invoke(context, handle(userId), 0);
    }
    @SuppressWarnings("unchecked") public static List<Object> users(UserManager manager) throws Exception {
        return (List<Object>) UserManager.class.getMethod("getUsers").invoke(manager);
    }
    public static Object field(Object object, String name) throws Exception {
        return object.getClass().getField(name).get(object);
    }
    public static boolean flag(Object info, String name) {
        try { return Boolean.TRUE.equals(info.getClass().getMethod(name).invoke(info)); }
        catch (Exception ignored) { return false; }
    }
    public static int mainUser(UserManager manager) throws Exception {
        try {
            Object main = UserManager.class.getMethod("getMainUser").invoke(manager);
            if (main instanceof UserHandle) return id((UserHandle) main);
        } catch (ReflectiveOperationException ignored) { }
        List<Object> users = users(manager);
        for (Object info : users) if (flag(info, "isMain")) return ((Number) field(info, "id")).intValue();
        for (Object info : users) if (flag(info, "isPrimary")) return ((Number) field(info, "id")).intValue();
        // Old phone builds without FLAG_MAIN: accept a real system user only if not headless.
        boolean headless;
        try { headless = Boolean.TRUE.equals(UserManager.class.getMethod("isHeadlessSystemUserMode").invoke(null)); }
        catch (NoSuchMethodException e) { headless = false; }
        if (!headless) for (Object info : users) {
            int id = ((Number) field(info, "id")).intValue();
            if (id == 0 && flag(info, "isSystemOnly")) continue;
            if (id == 0) return id;
        }
        throw new IllegalStateException("无法可靠识别主空间，已停止读取");
    }
    public static boolean unlocked(UserManager manager, int id) throws Exception {
        Method method = UserManager.class.getMethod("isUserUnlocked", UserHandle.class);
        return Boolean.TRUE.equals(method.invoke(manager, handle(id)));
    }
}
