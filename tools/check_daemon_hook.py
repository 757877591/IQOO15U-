#!/usr/bin/env python3
"""Execute the real V15 daemon callback/eligibility with host substitutes for Android/Xposed.
This checks invocation isolation, not OEM lifecycle or Android integration.
"""
from pathlib import Path
import argparse, tempfile
from tool_support import java_command, run_utf8, classpath
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT/'app/src/main/java/ls/iqooaugment/com'

STUBS = {
'android/content/Context.java': '''package android.content;
public class Context { public String getPackageName(){return "com.vivo.gamecube";}
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
public ClassLoader getClassLoader(){return getClass().getClassLoader();} }''',
'android/app/Application.java': 'package android.app; public class Application extends android.content.Context {}',
'android/app/Instrumentation.java': 'package android.app; public class Instrumentation {}',
'android/content/pm/PackageInfo.java': '''package android.content.pm;
public class PackageInfo { public String versionName; public long getLongVersionCode(){return 0;} }''',
'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager { public PackageInfo getPackageInfo(String p,int f){return new PackageInfo();} }''',
'de/robv/android/xposed/IXposedHookLoadPackage.java': '''package de.robv.android.xposed;
public interface IXposedHookLoadPackage { void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam p); }''',
'de/robv/android/xposed/callbacks/XC_LoadPackage.java': '''package de.robv.android.xposed.callbacks;
public class XC_LoadPackage { public static class LoadPackageParam { public String packageName,processName; } }''',
'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
public class XC_MethodHook {
public static class Unhook { public void unhook(){} }
public static class MethodHookParam {
public Object[] args; public Object thisObject; private Object result;
private final java.util.Map<String,Object> extras = new java.util.HashMap<>();
public boolean hasThrowable(){return false;} public Object getResult(){return result;}
public void setResult(Object r){result=r;} public void setObjectExtra(String k,Object v){extras.put(k,v);}
public Object getObjectExtra(String k){return extras.get(k);} }
protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
}''',
'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
public class XposedBridge {
public static XC_MethodHook callback; public static java.lang.reflect.Method method;
public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Method m,XC_MethodHook c){method=m;callback=c;return new XC_MethodHook.Unhook();}
public static void log(String s){}
public static void before(XC_MethodHook.MethodHookParam p) throws Throwable {callback.beforeHookedMethod(p);}
}''',
'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;
public class XposedHelpers {
public static Class<?> findClass(String n,ClassLoader c){try{return Class.forName(n,false,c);}catch(Exception e){throw new RuntimeException(e);}}
public static Object getObjectField(Object o,String n){try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
public static Object callMethod(Object o,String n,Object... a){throw new UnsupportedOperationException();}
public static Object callStaticMethod(Class<?> c,String n,Object... a){throw new UnsupportedOperationException();}
public static void findAndHookMethod(Class<?> c,String n,Object... a){throw new UnsupportedOperationException();}
}''',
'ls/iqooaugment/com/hook/BridgeClient.java': '''package ls.iqooaugment.com.hook;
import ls.iqooaugment.com.data.Config;
class BridgeClient {
Config config=Config.empty(); boolean peer=true; int events; Runnable event;
BridgeClient(android.content.Context c,String p){}
Config current(){return config;} boolean assistantReady(){return peer;}
void onLifecycleEvent(){events++;if(event!=null)event.run();}
void set(String k,Object v){} void onConfigChanged(Runnable r){} void start(){}
}''',
'ls/iqooaugment/com/hook/ForegroundResolver.java': '''package ls.iqooaugment.com.hook;
import ls.iqooaugment.com.core.AppKey;
class ForegroundResolver {
static AppKey foreground; static boolean eligible=true,fail; static Runnable duringRead;
ForegroundResolver(android.content.Context c,java.util.function.BooleanSupplier b){}
AppKey resolve(String expected){if(fail)throw new IllegalStateException();if(duringRead!=null)duringRead.run();
return foreground!=null && foreground.packageName.equals(expected)?foreground:null;}
boolean eligible(AppKey key){return eligible;}
}''',
'ls/iqooaugment/com/hook/AssistantHooks.java': '''package ls.iqooaugment.com.hook;
class AssistantHooks { AssistantHooks(android.content.Context c,ClassLoader l,BridgeClient b,ls.iqooaugment.com.core.GameCubeVersion v){} void install(){} }''',
'com/vivo/gamedaemon/h.java': '''package com.vivo.gamedaemon;
public class h { public String b="com.example.selected";
private void v0(java.util.List<?> list,boolean master,int oldPid,String oldPackage,boolean multiWindow){} }'''
}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json-jar',type=Path,required=True)
    args=parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='iqoo-daemon-hook-') as folder:
        root=Path(folder); sources=[]
        for name,source in STUBS.items():
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(source,encoding='utf-8');sources.append(path)
        sources += [*sorted((BASE/'core').glob('*.java')), BASE/'Constants.java',
                    BASE/'data/Config.java', BASE/'data/Target.java', BASE/'hook/ShoulderHook.java',
                    BASE/'hook/SelectedEligibility.java', ROOT/'tests/daemon15/Daemon15HookTests.java']
        result=run_utf8(*java_command('javac','--release','17','-encoding','UTF-8','-cp',args.json_jar,'-d',root,*sources))
        if result.stderr: print(result.stderr,end='')
        print(run_utf8(*java_command('java','-cp',classpath(root,args.json_jar),'ls.iqooaugment.com.hook.Daemon15HookTests')).stdout,end='')

if __name__=='__main__':main()
