package ls.iqooaugment.com.hook;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import java.util.List;
import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.data.Config;
import ls.iqooaugment.com.data.Target;

public final class Daemon15HookTests {
    private static int assertions;
    private static void check(boolean b) { assertions++; if (!b) throw new AssertionError(assertions); }
    private static void set(Object o,String name,Object value) throws Exception {
        var field=o.getClass().getDeclaredField(name);field.setAccessible(true);field.set(o,value);
    }
    private static MethodHookParam call(com.vivo.gamedaemon.h nativeObject, Object list) throws Throwable {
        MethodHookParam p=new MethodHookParam();p.thisObject=nativeObject;
        p.args=new Object[]{list,true,31415,"com.example.previous",false};
        XposedBridge.before(p);
        check(p.args[1].equals(true) && p.args[2].equals(31415)
            && p.args[3].equals("com.example.previous") && p.args[4].equals(false));
        check(p.getResult()==null);
        return p;
    }
    public static void main(String[] args) throws Throwable {
        ShoulderHook hook=new ShoulderHook();
        BridgeClient bridge=new BridgeClient(new android.content.Context(),"test");
        AppKey chosen=new AppKey(0,7,"com.example.selected");
        Config config=new Config(true,1,List.of(new Target(chosen,"target","space",true)));
        bridge.config=config; ForegroundResolver.foreground=chosen;
        SelectedEligibility eligibility=new SelectedEligibility(new android.content.Context(),bridge,()->true);
        set(hook,"bridge",bridge);set(hook,"eligibility",eligibility);set(hook,"ready",true);
        var install=ShoulderHook.class.getDeclaredMethod("installDaemon15",ClassLoader.class);
        install.setAccessible(true);install.invoke(hook,Daemon15HookTests.class.getClassLoader());
        check(XposedBridge.method.getName().equals("v0"));
        com.vivo.gamedaemon.h nativeObject=new com.vivo.gamedaemon.h();
        List<String> original=List.of("com.example.native");
        Object result=call(nativeObject,original).args[0];
        check(result!=original && result.equals(List.of("com.example.native",chosen.packageName)));
        check(original.equals(List.of("com.example.native")));
        check(bridge.events==1);
        // Native current package, never the old-package argument, determines admission.
        nativeObject.b="com.example.previous";check(call(nativeObject,original).args[0]==original);
        nativeObject.b=chosen.packageName;
        ForegroundResolver.foreground=new AppKey(10,7,chosen.packageName);
        check(call(nativeObject,original).args[0]==original);
        ForegroundResolver.foreground=new AppKey(0,8,chosen.packageName);
        check(call(nativeObject,original).args[0]==original);
        ForegroundResolver.foreground=chosen;
        bridge.peer=false;check(call(nativeObject,original).args[0]==original);bridge.peer=true;
        bridge.config=config.withMaster(false);check(call(nativeObject,original).args[0]==original);bridge.config=config;
        set(hook,"ready",false);check(call(nativeObject,original).args[0]==original);set(hook,"ready",true);
        ForegroundResolver.fail=true;check(call(nativeObject,original).args[0]==original);ForegroundResolver.fail=false;
        ForegroundResolver.eligible=false;check(call(nativeObject,original).args[0]==original);ForegroundResolver.eligible=true;
        ForegroundResolver.duringRead=()->bridge.config=config.withMaster(false);
        check(call(nativeObject,original).args[0]==original);ForegroundResolver.duringRead=null;bridge.config=config;
        ForegroundResolver.duringRead=eligibility::invalidate;
        check(call(nativeObject,original).args[0]==original);ForegroundResolver.duringRead=null;
        ForegroundResolver.duringRead=()->bridge.peer=false;
        check(call(nativeObject,original).args[0]==original);ForegroundResolver.duringRead=null;bridge.peer=true;
        check(call(nativeObject,null).args[0]==null);
        List<?> malformed=List.of(7);check(call(nativeObject,malformed).args[0]==malformed);
        List<String> existing=List.of(chosen.packageName);check(call(nativeObject,existing).args[0]==existing);
        nativeObject.b=null;check(call(nativeObject,original).args[0]==original);
        System.out.println("PASS: "+assertions+" real V15 daemon callback assertions with host Android/Xposed substitutes; no device claim.");
    }
}
