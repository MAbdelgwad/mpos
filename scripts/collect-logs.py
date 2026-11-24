#!/usr/bin/env python3
"""Collect bounded Compose diagnostics without exporting configuration or environment files."""
import argparse
import datetime
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--browser', type=Path, help='Optional downloaded mpos-browser-diagnostics.json')
parser.add_argument('--since', default='30m', help='Docker log time window, e.g. 30m or 2h')
args = parser.parse_args()
os.umask(0o077)
secrets = []
env = root / '.env'
if env.exists():
    for line in env.read_text().splitlines():
        if '=' not in line or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        if any(word in key.upper() for word in ('PASSWORD', 'TOKEN', 'SECRET', 'KEY')) and value.strip():
            secrets.append(value.strip().strip('"\''))

def redact(value):
    for secret in secrets:
        value = value.replace(secret, '[REDACTED]')
    value = re.sub(r'Bearer\s+[^\s"\']+', 'Bearer [REDACTED]', value, flags=re.I)
    value = re.sub(r'(?i)((?:password|token|secret|authorization|cookie)["\']?\s*[:=]\s*)[^\s,;]+', r'\1[REDACTED]', value)
    value = re.sub(r'[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', '[EMAIL]', value, flags=re.I)
    return value

compose = ['docker', 'compose', '--project-name', 'mpos-test-architecture', '--project-directory', str(root), '-f', str(root / 'compose.yaml')]
files = {}
def collect(name, command):
    try:
        result = subprocess.run(command, cwd=root, capture_output=True, text=True, timeout=45)
        value = result.stdout + '\n' + result.stderr
        files[name] = redact('exit_code=' + str(result.returncode) + '\n' + value).encode()
    except (OSError, subprocess.TimeoutExpired) as error:
        files[name] = ('Collection failed: ' + type(error).__name__).encode()

collect('services.txt', compose + ['ps', '-a'])
for service in ('api', 'web', 'db'):
    collect(service + '.log', compose + ['logs', '--no-color', '--timestamps', '--since', args.since, '--tail', '2000', service])
collect('docker-version.txt', ['docker', 'version', '--format', '{{.Client.Version}} / {{.Server.Version}}'])
collect('compose-version.txt', ['docker', 'compose', 'version'])
if args.browser:
    if args.browser.stat().st_size > 2_000_000:
        raise SystemExit('Browser report must be smaller than 2 MB.')
    browser = json.loads(args.browser.read_text())
    if browser.get('app') != 'mpos' or browser.get('schema_version') != 1:
        raise SystemExit('Not an MPOS browser diagnostics report.')
    files['browser.json'] = redact(json.dumps(browser, ensure_ascii=False, indent=2)).encode()
files['README.txt'] = b'API/web/db logs: last 2000 lines per service within requested window. No .env, database dump or Compose config included. Browser JavaScript errors are in browser.json only when explicitly supplied. Redaction is best effort; review before sharing.\n'
out = root / 'diagnostics'
out.mkdir(exist_ok=True, mode=0o700)
name = datetime.datetime.now(datetime.timezone.utc).strftime('mpos-diagnostics-%Y%m%dT%H%M%SZ.tar.gz')
path = out / name
with tarfile.open(path, 'w:gz') as archive:
    for filename, content in files.items():
        info = tarfile.TarInfo(filename)
        info.size = len(content)
        info.mode = 0o600
        archive.addfile(info, io.BytesIO(content))
path.chmod(0o600)
print('Diagnostic archive: ' + str(path))
if not args.browser:
    print('Browser JavaScript errors are separate: download Diagnostics from the app, then rerun with --browser PATH.')
