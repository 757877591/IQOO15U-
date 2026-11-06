"""Frozen 1500701 definitions and audited method-body fingerprints; no device claims."""
from pathlib import Path
import hashlib, json, zipfile
from verify_target_apk import Dex

def verify(apk_path):
    contract = json.loads(Path(__file__).with_name('target_1500701_contract.json').read_text(encoding='utf-8'))
    methods = set(contract['methods'])
    fields = set(contract['fields'])
    found = []
    with zipfile.ZipFile(apk_path) as apk:
        dex_files = sorted(n for n in apk.namelist() if n.startswith('classes') and n.endswith('.dex') and '/' not in n)
        for name in dex_files:
            dex = Dex(apk.read(name))
            found.extend(dict(dex=name, **row) for row in dex.definitions(methods, fields))
    definitions = {row['signature']: row for row in found}
    missing = sorted((methods | fields) - definitions.keys())
    mismatches = [sig for sig, expected in contract['methods'].items()
                  if sig in definitions and definitions[sig]['instruction_sha256'] != expected]
    sha = hashlib.sha256(Path(apk_path).read_bytes()).hexdigest()
    return {'apk_name': Path(apk_path).name, 'version_name': '15.0.07.01_de', 'version_code': 1500701,
            'sha256': sha, 'expected_sha256_match': sha == contract['sha256'], 'dex_count': len(dex_files),
            'required_definitions': len(methods | fields), 'found': found, 'missing': missing,
            'method_fingerprint_mismatches': mismatches,
            'audited_category_callers': contract['category_callers'],
            'pass': sha == contract['sha256'] and not missing and not mismatches,
            'scope': 'Exact uploaded APK bytes, defined symbols and frozen audited method bodies. '
                     'Category caller inventory was reviewed from disassembly; no Android or hardware behavior verified.'}
