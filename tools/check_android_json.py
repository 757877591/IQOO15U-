#!/usr/bin/env python3
"""Test actual data classes using a functional Android-style JSON implementation on host.
No dependency is downloaded and no jar is included in the module/runtime/source delivery.
"""
import argparse, hashlib, json, tempfile
from pathlib import Path
from tool_support import classpath, java_command, run_utf8
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/java/ls/iqooaugment/com'
EXPECTED='dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79'
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--json-jar',type=Path,required=True,help='Maven Central com.vaadin.external.google:android-json:0.0.20131108.vaadin1')
    a=p.parse_args()
    assert hashlib.sha256(a.json_jar.read_bytes()).hexdigest()==EXPECTED,'Unexpected test dependency checksum'
    with tempfile.TemporaryDirectory(prefix='iqoo-json-tests-') as folder:
        sources=[BASE/'Constants.java',*sorted((BASE/'core').glob('*.java')),
            *(BASE/'data'/name for name in ('Config.java','Target.java','Catalog.java','AppEntry.java')),
            ROOT/'tests/androidjson/DataParsingTests.java']
        run_utf8(*java_command('javac','--release','17','-encoding','UTF-8','-cp',a.json_jar.resolve(),'-d',folder,*sources))
        output=run_utf8(*java_command('java','-cp',classpath(folder,a.json_jar.resolve()),'DataParsingTests')).stdout.strip()
    print(output)
    (ROOT/'docs/data-parser-test-results.json').write_text(json.dumps({
        'suite':output,'json_implementation':'com.vaadin.external.google:android-json:0.0.20131108.vaadin1',
        'test_dependency_sha256':EXPECTED,'runtime_dependency_added':False,
        'scope':'Host actual Config/Target/Catalog/AppEntry parsing; not Android Binder/device integration'
    },ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':main()
