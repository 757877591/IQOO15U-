#!/usr/bin/env python3
"""Native-ownership and event-only source contracts. Not Android/device execution tests."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/java/ls/iqooaugment/com'
def read(name):return (BASE/name).read_text(encoding='utf-8')
def main():
    hooks=read('hook/AssistantHooks.java');daemon=read('hook/ShoulderHook.java');bridge=read('hook/BridgeClient.java')
    provider=read('data/ConfigProvider.java');eligibility=read('hook/SelectedEligibility.java');watch=read('hook/TaskWatcher.java')
    store=read('data/ConfigStore.java');ui=read('ui/MainActivity.java');model=read('ui/UiModel.java')
    readiness=read('core/RuntimeReadiness.java');all_hooks='\n'.join(x.read_text(encoding='utf-8') for x in (BASE/'hook').glob('*.java'))
    current=bridge.split('Config current() {',1)[1].split('boolean assistantReady()',1)[0]
    input_scope=hooks.split('// Two native visual category checks.',1)[1].split('// All other',1)[0]
    checks={
        'no session owner implementation':not any((BASE/'core'/n).exists() for n in ('SessionFence.java','DispatchContext.java','UiOverrideScope.java','RuntimeFreshness.java')),
        'no native input/driver takeover':not any(s in all_hooks for s in ('param.setResult(null)','setObjectField(', 'setBooleanField(', 'driverOwnerEpoch','submitDisabled','payload(', 'guardedCallback','pendingRetirement')),
        'no native driver invocation':not re.search(r'(?:callMethod|callStaticMethod|method|hook)\([^\n]*(?:"r"|"b"|"s")[,)]',hooks),
        'no native listener or close invocation':not re.search(r'(?:callMethod|callStaticMethod|method|hook)\([^\n]*(?:registerGameCubeListener|unregisterGameCubeListener|closeGameShoulder|resetAllBtnState)',hooks),
        'no session recovery or lifecycle replay':not any(s in hooks+daemon for s in ('recoverNativeSession','recheckNative','lastNativeArgs','rechecking','callMethod(object, "t0"')),
        'no global game list edit':'sput' not in daemon and 'setStaticObjectField' not in all_hooks,
        'daemon list only in exact OEM frame':'frame.get()' in daemon and 'getDeclaredMethod("t0", int.class, String.class)' in daemon,
        'only original list extension result':daemon.count('param.setResult(')==1 and 'param.setResult(result)' in daemon,
        'only capability/category boolean results':hooks.count('param.setResult(')==2 and 'param.setResult(true)' in hooks and 'param.setResult(false)' in hooks,
        'exact native generic driver category scope':'allow("be.a", "m", "", false, int.class, boolean.class)' in hooks,
        'exact native SDK category scope':'allow("be.a", "u", "", false, type(DETAILS))' in hooks,
        'nested native p stays cached':'boolean localOnly = category.cacheOnly()' in hooks and '? cachedGeneric((String) param.args[0]) : refreshGeneric' in hooks,
        'input category scopes only cached':input_scope.count(', true,')==2 and 'cachedGeneric(pkg)' in hooks,
        'no input listener/run hooks':not any(s in hooks for s in ('be.a$b','zd.h','zd.d','zd.f','"run"','"invoke"')),
        'no input call stack scans':'getStackTrace' not in all_hooks,
        'all scoped native barriers retained':all(s in hooks for s in ('barrier(method(K, "q"','barrier(method(BASE + "utils.l", "a"','barrier(method(E0, "V0"','barrier(method("mc.b", "h"','barrier(method("nb.h", "l"','barrier(method(BASE + "a", "onGameSdkEvent"')),
        'GAME_REVIEW classification unchanged':'GAME_REVIEW' not in hooks and '"GAME_SHOULDER"' in hooks,
        'only shoulder item style changed':hooks.count('param.args[0] = 1')==1 and '"n") != XposedHelpers.getStaticObjectField(type(ITEM_TYPE), "GAME_SHOULDER")' in hooks,
        'scope exact native presenter identity':'"T.b"' in hooks and '"N.T.b"' in hooks and '"A.T.b"' in hooks,
        'native settings are read-only':'Settings.Secure.getInt' in hooks and not re.search(r'Settings\.(Global|Secure|System)\.put',all_hooks),
        'native SDK transition fails closed':'nativeUser == selected.key.userId' in hooks and hooks.count('mapping_type_')==2,
        'config reference and task generation fenced':'bridge.current() == result.config' in eligibility and 'generation.get() == result.generation' in eligibility,
        'task event invalidates before queued read':'invalidate();\n                main.removeCallbacks(foregroundChanged)' in hooks,
        'no bridge lease names':not any(s in bridge for s in ('onOwnedState','ownedDeadline','ownedRenewal','leaseRenewal','assistantExpires','60_000','90_000','BRIDGE_FRESH_MS')),
        'cache read cannot queue IPC':not any(s in current for s in ('requestRefresh(', '.call(', '.post(', '.postDelayed(')),
        'event config observer synchronous revocation':'new ContentObserver(null)' in bridge and 'invalidateLocked(false); requestRefresh();' in bridge,
        'finite retry gate retained':'RefreshGate' in bridge and 'cancelRefreshLocked()' in bridge,
        'publisher death no polling':'Death never starts a provider read or retry' in bridge,
        'bridge publisher token':'putBinder("publisher", publisher)' in bridge,
        'provider epoch linked':'endpoint.linkToDeath(providerDeath, 0)' in bridge,
        'stale response invalidated':'invalidation.matches(attempt)' in bridge,
        'stale death fenced':'if (provider != endpoint) return' in bridge and 'if (assistant != peer || assistantGeneration != peerGeneration) return' in bridge,
        'publisher replacement fenced':'registered.token != token' in provider and 'PUBLISHERS.get(key) == this' in provider,
        'generation bound protocol':'publisherGeneration' in bridge+provider,
        'shared UID boundary documented':'UID1000' in provider,
        'no readiness expiry':'assistantExpiry' not in provider+bridge and 'expires' not in readiness.lower(),
        'only explicit readiness events':'"ready".equals(uri.getLastPathSegment())' in bridge and '"renewed"' not in bridge+provider,
        'status observer stays out of runtime payload':'appendPath("status")' not in bridge,
        'strict status parsing':'JsonSafety.requireDocument(raw, 24_000, 8)' in provider,
        'strict config save CAS':'config.revision != previous.revision + 1' in store,
        'same raw config parsed once':'raw.equals(cachedRaw)' in store,
        'runtime status only memory':'LIVE_STATUS' in store and 'legacy.edit().clear().commit()' in store,
        'UI visible lifecycle':'protected void onStop()' in ui and 'if (!visible) return' in ui,
        'hidden dialogs deferred':'current.canShowDialogs()' in model and 'pendingScanSelection' in model,
        'task observer cleanup finite':'cleanupRetries.failed(cleanupOwner)' in watch,
        'no timer-based foreground refresh':'postDelayed(foregroundChanged' not in hooks,
        'no silent replacement signing key':'-genkeypair' not in (ROOT/'release.sh').read_text(encoding='utf-8')+(ROOT/'release.ps1').read_text(encoding='utf-8'),
        'selection change warns native close':'先松开肩键并退出目标应用' in ui,
    }
    for name,passed in checks.items():assert passed,name
    print(f'PASS: {len(checks)} event-only/native-ownership source contracts (not Android/hardware integration).')
if __name__=='__main__':main()
