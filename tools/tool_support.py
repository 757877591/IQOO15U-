"""Portable host-tool invocation; all project text and captured output use UTF-8."""
from pathlib import Path
import os
import subprocess


def classpath(*paths):
    return os.pathsep.join(map(str, paths))


def java_command(executable, *arguments):
    # JDK 17 still follows the Windows code page unless explicitly configured.
    prefix = '-J' if Path(executable).stem.lower() == 'javac' else ''
    options = [prefix + option for option in (
        '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8')]
    return [str(executable), *options, *map(str, arguments)]


def run_utf8(*command):
    environment = os.environ.copy()
    environment['PYTHONIOENCODING'] = 'utf-8'
    return subprocess.run(list(map(str, command)), text=True, encoding='utf-8',
        capture_output=True, check=True, env=environment)


def sdk_command(build_tools, name, *arguments):
    folder = Path(build_tools)
    suffix = ('.bat' if name == 'apksigner' else '.exe') if os.name == 'nt' else ''
    executable = folder / (name + suffix)
    if not executable.is_file():
        raise FileNotFoundError('Missing Android build tool: ' + str(executable))
    if suffix == '.bat':
        # Calling batch files can make cmd.exe reinterpret otherwise literal paths.
        # Use the same SDK backend directly so argument boundaries stay intact.
        backend = folder / 'lib' / 'apksigner.jar'
        if not backend.is_file():
            raise FileNotFoundError('Missing Android signing backend: ' + str(backend))
        return java_command('java', '-jar', backend, *arguments)
    return [str(executable), *map(str, arguments)]


def sdk_output(build_tools, name, *arguments):
    return run_utf8(*sdk_command(build_tools, name, *arguments)).stdout
