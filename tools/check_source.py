#!/usr/bin/env python3
"""Run real host-JDK logic tests and Java syntax parsing. Not an Android build."""
from pathlib import Path
import json, re, shutil, sys, tempfile, xml.etree.ElementTree as ET
from tool_support import java_command, run_utf8
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/java/ls/iqooaugment/com'
def run(*args):
    result=run_utf8(*args)
    print(result.stdout,end='')
    if result.stderr:print(result.stderr,end='')
    return result.stdout.strip()
def main():
    for executable in ('java','javac'):
        if not shutil.which(executable):raise SystemExit('A JDK 17+ is required: '+executable)
    with tempfile.TemporaryDirectory(prefix='iqoo-check-') as folder:
        run(*java_command('javac','--release','17','-encoding','UTF-8','-d',folder,*map(str,(BASE/'core').glob('*.java')),*map(str,sorted((ROOT/'tests').glob('*.java'))),str(ROOT/'tools/ParseJava.java')))
        core=run(*java_command('java','-cp',folder,'CoreTests'))
        regressions={test.stem:run(*java_command('java','-cp',folder,test.stem)) for test in sorted((ROOT/'tests').glob('*.java')) if test.stem != 'CoreTests'}
        syntax=run(*java_command('java','-cp',folder,'ParseJava',str(BASE)))
    ui_contract=run(sys.executable,str(ROOT/'tools/check_ui_contract.py'))
    power_contract=run(sys.executable,str(ROOT/'tools/check_power_contract.py'))
    xmls=list((ROOT/'app/src/main').rglob('*.xml'))
    for file in xmls:ET.parse(file)
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    NS='{http://schemas.android.com/apk/res/android}'
    metadata = {node.attrib[NS+'name']: node.attrib.get(NS+'value') for node in manifest.findall('application/meta-data')}
    assert metadata['xposeddescription'] == '@string/module_description'
    descriptions = [node.text for file in (ROOT/'app/src/main/res').rglob('*.xml') for node in ET.parse(file).getroot().findall('string') if node.attrib.get('name') == 'module_description']
    assert descriptions == ['扩展iQOO 15Ultra肩键支持应用范围'], descriptions
    permissions={node.attrib[NS+'name'] for node in manifest.findall('uses-permission')}
    assert permissions=={'android.permission.INTERNET','android.permission.REQUEST_INSTALL_PACKAGES'},permissions
    update_provider=next(node for node in manifest.findall('application/provider') if node.attrib[NS+'name']=='.update.UpdateApkProvider')
    assert update_provider.attrib[NS+'exported']=='false' and update_provider.attrib[NS+'grantUriPermissions']=='true'
    assert {node.attrib[NS+'name'] for node in manifest.findall('queries/package')} == {'com.vivo.gamecube','org.lsposed.manager'}
    for source in BASE.rglob('*.java'):
        assert not re.search(r'Settings\.(?:Global|Secure|System)\.(?:put\w*|set\w*)\s*\(', source.read_text(encoding='utf-8')),source
    clipboard=(BASE/'ui/MainActivity.java').read_text(encoding='utf-8').split('private void copyDiagnostics(){',1)[1].split('public void showError',1)[0]
    assert 'Diagnostics.summary(' in clipboard
    assert not any(raw in clipboard for raw in ('config.encode(', 'Build.MODEL', '.daemon)', '.assistant)'))
    hook=(BASE/'hook/ShoulderHook.java').read_text(encoding='utf-8')
    assert 'LOG_LINES.getAndUpdate(value -> value < 17 ? value + 1 : 17)' in hook
    assert 'if (line < 16)' in hook and 'else if (line == 16)' in hook
    module=(ROOT/'app/src/main/assets/xposed_init').read_text(encoding='utf-8').strip()
    assert module=='ls.iqooaugment.com.hook.ShoulderHook'
    assert 'compileOnly' in (ROOT/'app/build.gradle').read_text(encoding='utf-8')
    assert 'minSdk 29' in (ROOT/'app/build.gradle').read_text(encoding='utf-8')
    gradle=(ROOT/'app/build.gradle').read_text(encoding='utf-8')
    assert "namespace 'ls.iqooaugment.com'" in gradle
    assert "applicationId 'ls.iqooaugment.com'" in gradle
    assert 'debuggable false' in gradle
    assert 'versionCode 10011' in gradle
    assert "versionName '0.1.0-alpha11'" in gradle
    assert 'enableV2Signing true' in gradle
    assert manifest.find('application/provider').attrib[NS+'authorities']=='${applicationId}.config'
    assert '"ls.iqooaugment.com"' in (BASE/'Constants.java').read_text(encoding='utf-8')
    assert 'RootMain.class.getName()' in (BASE/'root/RootClient.java').read_text(encoding='utf-8')
    for source in BASE.rglob('*.java'):
        assert 'com.lili.iqooshoulder' not in source.read_text(encoding='utf-8'),source
    print('PASS: release applicationId, namespace, provider, root entry, signing configuration and self-filter migration checks.')
    result={'core_logic':core,'ui_source_contract':ui_contract,'power_security_source_contract':power_contract,'java_syntax':syntax,'xml_well_formed_files':len(xmls),'declared_permissions':sorted(permissions),
            'regression_suites':regressions,
            'android_compile':'Separate build stage; see current Release verification report',
            'android_lint':'Separate build stage; see current Release verification report','ui_rendering':'NOT RUN','device_integration':'NOT RUN',
            'note':'Metric assertions check source constants, not measured UI layout. XML checks are not AAPT compilation.'}
    (ROOT/'docs').mkdir(exist_ok=True)
    (ROOT/'docs/host-check-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('PASS: XML well-formedness, module entry and declared-permission checks.')
if __name__=='__main__':main()
