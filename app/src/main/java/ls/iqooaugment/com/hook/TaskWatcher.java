package ls.iqooaugment.com.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import ls.iqooaugment.com.core.CleanupRetries;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/** Optional task observation. Master-off unregisters it; late callbacks are ignored before payload access. */
final class TaskWatcher {
    private Object listener, service;
    private Method register, unregister;
    private Context context;
    private BroadcastReceiver screenReceiver;
    private boolean taskRegistered, receiverRegistered;
    private volatile boolean observing;
    private final Handler cleanupHandler = new Handler(Looper.getMainLooper());
    private final CleanupRetries cleanupRetries = new CleanupRetries();
    private final Object cleanupOwner = new Object();
    private final Runnable cleanupTask = () -> { try { stop(); } catch (Exception ignored) { } };
    synchronized void prepare(Context context, Runnable changed) throws Exception {
        if (listener != null) return;
        this.context = context;
        Class<?> iface = Class.forName("android.app.ITaskStackListener");
        Class<?> stub = Class.forName("android.app.ITaskStackListener$Stub");
        Set<Integer> codes = new HashSet<>(), changedCodes = new HashSet<>();
        Set<String> changedNames = Set.of("onTaskStackChanged", "onTaskCreated", "onTaskRemoved",
            "onTaskMovedToFront", "onTaskMovedToBack", "onTaskRemovalStarted", "onActivityPinned",
            "onActivityUnpinned", "onTaskDisplayChanged", "onTaskFocusChanged", "onTaskWindowingModeChanged");
        for (Field field : stub.getDeclaredFields()) if (field.getName().startsWith("TRANSACTION_")) {
            field.setAccessible(true); int code = field.getInt(null); codes.add(code);
            if (changedNames.contains(field.getName().substring("TRANSACTION_".length()))) changedCodes.add(code);
        }
        if (codes.isEmpty() || changedCodes.isEmpty()) throw new IllegalStateException("Task listener transaction map unavailable");
        service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Class<?> serviceInterface = Class.forName("android.app.IActivityTaskManager");
        register = serviceInterface.getMethod("registerTaskStackListener", iface);
        unregister = serviceInterface.getMethod("unregisterTaskStackListener", iface);
        register.setAccessible(true); unregister.setAccessible(true);
        final class CallbackBinder extends Binder {
            void init(IInterface owner) { attachInterface(owner, iface.getName()); }
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(iface.getName());
                    return true;
                }
                if (codes.contains(code)) {
                    data.enforceInterface(iface.getName());
                    // No task payload is interpreted or retained, including late callbacks after stop.
                    if (observing && changedCodes.contains(code)) changed.run();
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        }
        CallbackBinder binder = new CallbackBinder();
        listener = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "asBinder": return binder;
                case "toString": return "IQOOShoulderTaskListener";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return args != null && proxy == args[0];
                default: if (observing && changedNames.contains(method.getName())) changed.run(); return null;
            }
        });
        binder.init((IInterface) listener);
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { if (observing) changed.run(); }
        };
    }
    synchronized void start() throws Exception {
        if (observing) return;
        if (listener == null) throw new IllegalStateException("Task observer not prepared");
        cleanupHandler.removeCallbacks(cleanupTask);
        if (taskRegistered || receiverRegistered) stop(); // Never re-use an uncertain partial registration.
        cleanupRetries.completed(cleanupOwner);
        try {
            if (!taskRegistered) { taskRegistered = true; register.invoke(service, listener); }
            if (!receiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_USER_PRESENT); filter.addAction("android.intent.action.USER_SWITCHED");
                filter.addAction("android.intent.action.USER_STOPPED"); filter.addAction(Intent.ACTION_USER_UNLOCKED);
                receiverRegistered = true; // Retain cleanup ownership even if registration throws after IPC.
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                else context.registerReceiver(screenReceiver, filter);
                receiverRegistered = true;
            }
            observing = true;
        } catch (Exception e) {
            try { stop(); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    synchronized void stop() throws Exception {
        // Close the gate before IPC; even an unregister failure cannot trigger foreground reads.
        observing = false;
        Exception error = null;
        if (taskRegistered) {
            try { unregister.invoke(service, listener); taskRegistered = false; }
            catch (Exception e) { error = e; }
        }
        if (receiverRegistered) {
            try { context.unregisterReceiver(screenReceiver); receiverRegistered = false; }
            catch (IllegalArgumentException absent) { receiverRegistered = false; }
            catch (Exception e) { if (error == null) error = e; else error.addSuppressed(e); }
        }
        if (error != null) {
            long delay = cleanupRetries.failed(cleanupOwner);
            cleanupHandler.removeCallbacks(cleanupTask);
            if (delay >= 0) cleanupHandler.postDelayed(cleanupTask, delay);
            throw error;
        }
        cleanupHandler.removeCallbacks(cleanupTask); cleanupRetries.completed(cleanupOwner);
    }
    boolean isRunning() { return observing; }
}
