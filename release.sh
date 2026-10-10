#!/usr/bin/env bash
# Build and verify a signed, non-debuggable release. Local test keys are reused, not regenerated.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK" && -f "$SDK/platforms/android-35/android.jar" ]] || {
    echo 'Install Android SDK Platform 35 and Build-Tools 35.0.0; set ANDROID_HOME.' >&2; exit 2;
}
TOOLS="$SDK/build-tools/35.0.0"
for item in apksigner zipalign aapt; do [[ -x "$TOOLS/$item" ]] || { echo "Missing: $TOOLS/$item" >&2; exit 2; }; done
for item in java javac keytool python3; do command -v "$item" >/dev/null; done
count=0
for name in IQOO_KEYSTORE IQOO_STORE_PASSWORD IQOO_KEY_ALIAS IQOO_KEY_PASSWORD; do
    if [[ -n "${!name:-}" ]]; then count=$((count+1)); fi
done
[[ "$count" == 0 || "$count" == 4 ]] || { echo 'Supply all four IQOO signing variables, or none.' >&2; exit 2; }
if [[ "$count" == 0 ]]; then
    umask 077
    SIGN="$ROOT/.signing"
    export IQOO_KEYSTORE="$SIGN/iqoo-release.p12" IQOO_KEY_ALIAS=iqoo-local-release
    if [[ -e "$IQOO_KEYSTORE" && ! -f "$SIGN/credentials.json" ]] || [[ ! -e "$IQOO_KEYSTORE" && -f "$SIGN/credentials.json" ]]; then
        echo 'Incomplete .signing directory. Restore the matching key and credentials.' >&2; exit 2
    fi
    if [[ ! -e "$IQOO_KEYSTORE" ]]; then
        echo 'Signing identity missing. Restore the existing PRIVATE signing backup; never silently generate a replacement.' >&2
        exit 2
    fi
    export IQOO_STORE_PASSWORD="$(python3 - "$SIGN/credentials.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1],encoding='utf-8-sig'))['password'])
PY
)"
    export IQOO_KEY_ALIAS="$(python3 - "$SIGN/credentials.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1],encoding='utf-8-sig'))['alias'])
PY
)"
    export IQOO_KEY_PASSWORD="$IQOO_STORE_PASSWORD"
fi
[[ -f "$IQOO_KEYSTORE" ]] || { echo 'Signing keystore not found.' >&2; exit 2; }
export ANDROID_HOME="$SDK"
bash "$ROOT/build.sh" :app:assembleRelease :app:lintRelease
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo 'Signed Release APK was not produced.' >&2; exit 3; }
mkdir -p "$ROOT/dist"
"$TOOLS/apksigner" verify --verbose --print-certs "$APK" > "$ROOT/dist/signature-verification.txt"
"$TOOLS/zipalign" -c -P 16 -v 4 "$APK" > "$ROOT/dist/zipalign-verification.txt"
"$TOOLS/aapt" dump badging "$APK" > "$ROOT/dist/apk-badging.txt"
python3 - "$APK" "$ROOT" <<'PY'
import hashlib,json,pathlib,re,shutil,sys,zipfile
apk=pathlib.Path(sys.argv[1]);root=pathlib.Path(sys.argv[2]);dist=root/'dist'
badging=(dist/'apk-badging.txt').read_text()
if "package: name='ls.iqooaugment.com'" not in badging or 'application-debuggable' in badging:
    raise SystemExit('Wrong package name or debuggable APK.')
with zipfile.ZipFile(apk) as z:
    if 'classes.dex' not in z.namelist():raise SystemExit('Missing classes.dex')
    if z.read('assets/xposed_init').decode().strip()!='ls.iqooaugment.com.hook.ShoulderHook':
        raise SystemExit('Wrong Xposed entry')
meta=json.loads((apk.parent/'output-metadata.json').read_text())
version=meta['elements'][0]['versionName']
if not re.fullmatch(r'[A-Za-z0-9.+_-]+',version):raise SystemExit('Unsafe version name')
name=f'IQOOShoulderExtension-v{version}-release.apk';out=dist/name
shutil.copyfile(apk,out)
digest=hashlib.sha256(out.read_bytes()).hexdigest()
(dist/'SHA256SUMS.txt').write_text(f'{digest}  {name}\n')
print(f'Verified signed Release APK: {out}\nSHA-256: {digest}')
PY
