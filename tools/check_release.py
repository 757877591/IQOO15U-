#!/usr/bin/env python3
"""Check a real release APK and optional previous signing identity. No device claims."""
from pathlib import Path
import argparse, hashlib, json, re, zipfile
from verify_target_apk import Dex
from tool_support import sdk_output

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--build-tools', required=True, type=Path)
    parser.add_argument('--previous', type=Path)
    parser.add_argument('--expect-new-signer', action='store_true', help='Assert a deliberately authorized signer change against --previous')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.expect_new_signer and not args.previous: parser.error('--expect-new-signer requires --previous')
    badging = sdk_output(args.build_tools, 'aapt', 'dump', 'badging', args.apk)
    permissions = sdk_output(args.build_tools, 'aapt', 'dump', 'permissions', args.apk)
    manifest = sdk_output(args.build_tools, 'aapt', 'dump', 'xmltree', args.apk, 'AndroidManifest.xml')
    resources = sdk_output(args.build_tools, 'aapt', 'dump', '--values', 'resources', args.apk)
    description_ref = re.search(r'"xposeddescription"[^\n]*\n\s+A: android:value\([^)]*\)=@(0x[0-9a-f]+)', manifest)
    assert description_ref, 'Missing LSPosed description resource reference'
    description = re.search(r'\bresource ' + description_ref.group(1) + r' ls\.iqooaugment\.com:string/module_description:[^\n]*\n\s+\(string(?:8|16)\) "([^"]*)"', resources)
    assert description and description.group(1) == '扩展iQOO 15Ultra肩键支持应用范围', 'Wrong packaged LSPosed description'
    signatures = sdk_output(args.build_tools, 'apksigner', 'verify', '--verbose', '--print-certs', args.apk)
    alignment = sdk_output(args.build_tools, 'zipalign', '-c', '-P', '16', '-v', '4', args.apk)
    assert "package: name='ls.iqooaugment.com' versionCode='10010' versionName='0.1.0-alpha10'" in badging
    assert 'application-debuggable' not in badging
    assert not re.search(r'uses-permission', permissions + manifest)
    assert 'ls.iqooaugment.com.config' in manifest
    assert 'com.vivo.gamecube' in manifest and 'org.lsposed.manager' in manifest
    assert 'android:allowBackup' in manifest and 'android:allowBackup(0x01010280)=(type 0x12)0x0' in manifest
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signatures
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signatures).group(1)
    assert cert == '7181eae8b831a960d040f9f5c00b656f08849b080751772e81c66c37d01224f2', 'Expected retained release signing identity'
    previous_cert = None
    if args.previous:
        previous_signature = sdk_output(args.build_tools, 'apksigner', 'verify', '--print-certs', args.previous)
        previous_cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', previous_signature).group(1)
        if args.expect_new_signer: assert cert != previous_cert, 'Expected an approved new signing identity'
        else: assert cert == previous_cert, 'Signing identity changed'
    with zipfile.ZipFile(args.apk) as archive:
        assert archive.testzip() is None
        assert archive.read('assets/xposed_init').decode().strip() == 'ls.iqooaugment.com.hook.ShoulderHook'
        dex = Dex(archive.read('classes.dex'))
        definitions = {dex.types[dex.u4(dex.u4(100)+i*32)] for i in range(dex.u4(96))}
        assert 'Lls/iqooaugment/com/root/RootMain;' in definitions
        assert 'Lls/iqooaugment/com/hook/ShoulderHook;' in definitions
        assert all(name.startswith('Lls/iqooaugment/com/') for name in definitions)
        assert not any(name in '\n'.join(definitions) for name in ('SessionFence','DispatchContext','UiOverrideScope')), 'Obsolete takeover classes packaged'
        assert not any('Landroid/provider/Settings$' in method and '->put' in method for method in dex.methods)
        assert not any(part in '\n'.join(dex.types) for part in ('Ljava/net/Socket;', 'Ljava/net/HttpURLConnection;', 'Landroid/webkit/WebView;'))
    result = {'apk': args.apk.name, 'package': 'ls.iqooaugment.com', 'version_name': '0.1.0-alpha10',
        'version_code': 10010, 'bytes': args.apk.stat().st_size, 'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        'module_description': description.group(1), 'packaged_description_check': 'PASS',
        'declared_permissions': [], 'manifest_check': 'PASS', 'signature_v2': 'PASS',
        'signer_sha256': cert, 'previous_signer_matches': previous_cert == cert if previous_cert else None,
        'new_signer_expected': args.expect_new_signer, 'previous_signer_sha256': previous_cert,
        'zip_alignment': 'PASS', 'xposed_and_root_entries': 'PASS', 'defined_dex_classes': len(definitions),
        'settings_write_references': 'NONE', 'device_testing': 'NOT RUN'}
    if args.output: args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__': main()
