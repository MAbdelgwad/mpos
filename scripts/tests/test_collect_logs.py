import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest

class CollectorTest(unittest.TestCase):
    def test_collects_bounded_logs_and_redacts_credentials(self):
        source = Path(__file__).resolve().parents[1] / 'collect-logs.py'
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'scripts').mkdir()
            shutil.copy2(source, root / 'scripts/collect-logs.py')
            (root / '.env').write_text('DATABASE_PASSWORD=database-private-value\nMPOS_ADMIN_PASSWORD=admin-private-value\n')
            (root / 'compose.yaml').write_text('services: {}\n')
            binary = root / 'bin'
            binary.mkdir()
            fake = binary / 'docker'
            fake.write_text('#!/bin/sh\nprintf "%s\\n" "database-private-value admin-private-value Authorization: Bearer opaque-private-value user@example.com"\n')
            fake.chmod(0o755)
            report = root / 'browser.json'
            report.write_text(json.dumps({'app': 'mpos', 'schema_version': 1, 'events': []}))
            environment = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'])
            subprocess.run(['python3', str(root / 'scripts/collect-logs.py'), '--browser', str(report)], env=environment, capture_output=True, check=True)
            archive = next((root / 'diagnostics').glob('*.tar.gz'))
            self.assertEqual(0o600, archive.stat().st_mode & 0o777)
            with tarfile.open(archive) as package:
                self.assertEqual({'services.txt','api.log','web.log','db.log','docker-version.txt','compose-version.txt','browser.json','README.txt'}, set(package.getnames()))
                contents = '\n'.join(package.extractfile(entry).read().decode() for entry in package.getmembers())
                for secret in ['database-private-value','admin-private-value','opaque-private-value','user@example.com']:
                    self.assertNotIn(secret, contents)
                self.assertNotIn('.env', package.getnames())

if __name__ == '__main__': unittest.main()
