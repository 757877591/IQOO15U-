#!/usr/bin/env python3
"""Exercise host-tool portability with real Java and temporary SDK stand-ins.
No signing identity, APK, Android build or device is used.
"""
from pathlib import Path
import argparse
import contextlib
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import check_android_json
from tool_support import classpath, java_command, run_utf8, sdk_command, sdk_output

TOOLS = Path(__file__).resolve().parent
JSON_JAR = None


class ToolCompatibilityTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='iqoo-tool-tests-')
        self.addCleanup(temporary.cleanup)
        self.folder = Path(temporary.name)

    def compile(self, destination, *sources):
        destination.mkdir(parents=True, exist_ok=True)
        run_utf8(*java_command('javac', '-J-Dfile.encoding=GBK', '--release', '17',
            '-encoding', 'UTF-8', '-d', destination, *sources))

    def test_real_java_utf8_and_two_classpath_entries(self):
        source = self.folder / '源码 空格'
        source.mkdir()
        dependency = source / 'Greeting.java'
        library = self.folder / '依赖 空格'
        entry = source / 'Entry.java'
        # A method call forces runtime dependency loading instead of javac constant inlining.
        dependency.write_text('public class Greeting { public static String text() { return "肩键扩展"; } }', encoding='utf-8')
        self.compile(library, dependency)
        entry.write_text('public class Entry { public static void main(String[] args) { System.out.println(Greeting.text()); } }', encoding='utf-8')
        application = self.folder / '主类 空格'
        application.mkdir()
        run_utf8(*java_command('javac', '-J-Dfile.encoding=GBK', '--release', '17',
            '-encoding', 'UTF-8', '-cp', library, '-d', application, entry))
        result = run_utf8(*java_command('java', '-cp', classpath(application, library), 'Entry'))
        self.assertEqual('肩键扩展', result.stdout.strip())
        if os.name == 'nt':
            with self.assertRaises(subprocess.CalledProcessError):
                run_utf8(*java_command('java', '-cp', str(application) + ':' + str(library), 'Entry'))

    def test_real_parse_java_with_gbk_default_charset(self):
        classes = self.folder / 'parser'
        self.compile(classes, TOOLS / 'ParseJava.java')
        source = self.folder / 'syntax'
        source.mkdir()
        (source / 'Chinese.java').write_text('class Chinese { String value = "肩键扩展🧪"; }', encoding='utf-8')
        result = run_utf8(*java_command('java', '-Dfile.encoding=GBK', '-cp', classes, 'ParseJava', source))
        self.assertIn('PASS: parsed 1 Java source files.', result.stdout)

    @unittest.skipUnless(os.name == 'nt', 'Windows SDK layout regression')
    def test_windows_signer_backend_preserves_literal_arguments(self):
        sdk = self.folder / 'SDK 空格 & %PATH% !'
        (sdk / 'lib').mkdir(parents=True)
        (sdk / 'apksigner.bat').write_text('@exit /b 93\r\n', encoding='utf-8')
        source = self.folder / 'FakeSigner.java'
        source.write_text('public class FakeSigner { public static void main(String[] args) { '
            'System.out.println("中文资源输出"); for (String arg : args) System.out.println(arg); } }', encoding='utf-8')
        classes = self.folder / 'fake-signer-classes'
        self.compile(classes, source)
        with zipfile.ZipFile(sdk / 'lib' / 'apksigner.jar', 'w') as archive:
            archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nMain-Class: FakeSigner\r\n\r\n')
            archive.write(classes / 'FakeSigner.class', 'FakeSigner.class')
        arguments = ['verify', '--print-certs', str(self.folder / '包名 & %PATH% ! ^ (1).apk')]
        self.assertEqual(['中文资源输出', *arguments], sdk_output(sdk, 'apksigner', *arguments).splitlines())
        (sdk / 'apksigner.bat').unlink()
        (sdk / 'apksigner').write_text('extensionless decoy', encoding='utf-8')
        with self.assertRaises(FileNotFoundError):
            sdk_command(sdk, 'apksigner', *arguments)

    @unittest.skipUnless(os.name == 'nt', 'Windows native executable layout regression')
    def test_windows_exe_selection_and_utf8_output(self):
        sdk = self.folder / 'native SDK'
        sdk.mkdir()
        # A copied Python executable is a harmless real PE executable that emits SDK-like text.
        shutil.copy2(sys.executable, sdk / 'aapt.exe')
        for library in Path(sys.executable).parent.glob('*.dll'):
            shutil.copy2(library, sdk / library.name)
        arguments = ['dump', '--values', 'resources', '字面量 & %PATH% !.apk']
        code = 'import sys; print("肩键扩展资源"); print("|".join(sys.argv[1:]))'
        with patch.dict(os.environ, {'PYTHONHOME': sys.base_prefix}):
            result = sdk_output(sdk, 'aapt', '-c', code, *arguments)
        self.assertEqual(['肩键扩展资源', '|'.join(arguments)], result.splitlines())
        (sdk / 'aapt.exe').unlink()
        (sdk / 'aapt').write_text('extensionless decoy', encoding='utf-8')
        with self.assertRaises(FileNotFoundError):
            sdk_command(sdk, 'aapt', 'dump')

    @unittest.skipUnless(os.name == 'nt', 'Windows cp936 locale regression')
    def test_contract_readers_in_real_cp936_python_process(self):
        fixture = self.folder / '中文 fixture.txt'
        fixture.write_text('肩键扩展🧪', encoding='utf-8')
        worker = '''import locale, pathlib, sys
locale.setlocale(locale.LC_ALL, 'Chinese_China.936')
assert locale.getencoding().lower() in ('cp936', 'gbk'), locale.getencoding()
assert not sys.flags.utf8_mode
fixture = pathlib.Path(sys.argv[1])
try:
    corrupt = fixture.read_text()
except UnicodeDecodeError:
    corrupt = None
assert corrupt != '肩键扩展🧪', 'Negative control unexpectedly used UTF-8'
assert fixture.read_text(encoding='utf-8') == '肩键扩展🧪'
import check_ui_contract, check_power_contract, check_source
check_ui_contract.main()
check_power_contract.main()
assert check_source.run(sys.executable, '-c', 'print("肩键扩展")') == '肩键扩展'
print('PASS: cp936 Python source reads and UTF-8 subprocess output')
'''
        environment = os.environ.copy()
        environment.update(PYTHONUTF8='0', PYTHONIOENCODING='utf-8', PYTHONPATH=str(TOOLS))
        result = subprocess.run([sys.executable, '-X', 'utf8=0', '-c', worker, str(fixture)],
            capture_output=True, encoding='utf-8', check=True, env=environment)
        self.assertIn('PASS: cp936 Python source reads and UTF-8 subprocess output', result.stdout)

    def test_actual_android_json_runner_keeps_report_in_temporary_folder(self):
        if JSON_JAR is None:
            self.skipTest('Pass --json-jar to exercise the separately verified Android JSON dependency')
        (self.folder / 'docs').mkdir()
        tests = self.folder / 'tests' / 'androidjson'
        tests.mkdir(parents=True)
        shutil.copy2(TOOLS.parent / 'tests/androidjson/DataParsingTests.java', tests)
        shutil.copy2(TOOLS.parent / 'tests/androidjson/UpdateManifestTests.java', tests)
        output = io.StringIO()
        with patch.object(check_android_json, 'ROOT', self.folder), \
                patch.object(sys, 'argv', ['check_android_json.py', '--json-jar', str(JSON_JAR)]), \
                contextlib.redirect_stdout(output):
            check_android_json.main()
        report = json.loads((self.folder / 'docs/data-parser-test-results.json').read_text(encoding='utf-8'))
        self.assertEqual('PASS: 33 Android-style JSON parser integration assertions (host only)', report['suite'])
        self.assertIn(report['suite'], output.getvalue())
        self.assertEqual('PASS: 20 strict update-manifest parsing checks.', report['update_manifest_suite'])


def main():
    global JSON_JAR
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json-jar', type=Path)
    arguments = parser.parse_args()
    JSON_JAR = arguments.json_jar.resolve() if arguments.json_jar else None
    for executable in ('java', 'javac'):
        if not shutil.which(executable):
            raise SystemExit('A JDK 17+ is required: ' + executable)
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(ToolCompatibilityTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)


if __name__ == '__main__':
    main()
