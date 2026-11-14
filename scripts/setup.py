#!/usr/bin/env python3
import os
from pathlib import Path
import secrets
import sys

root = Path(__file__).resolve().parent.parent
env = root / '.env'
if not env.exists():
    if '--show-login' in sys.argv:
        raise SystemExit('Run bash scripts/start.sh first.')
    content = ('DATABASE_PASSWORD=' + secrets.token_hex(24) + '\n'
               'MPOS_ADMIN_EMAIL=admin@example.com\n'
               'MPOS_ADMIN_PASSWORD=' + secrets.token_hex(16) + '\n'
               'MPOS_API_PORT=18180\nMPOS_WEB_PORT=18181\n')
    fd = os.open(env, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(content)
    print('Created local credentials in .env (permissions 600).')
if '--show-login' in sys.argv:
    values = dict(line.split('=', 1) for line in env.read_text().splitlines() if line and not line.startswith('#'))
    print('Open: http://localhost:' + values.get('MPOS_WEB_PORT', '18181'))
    print('Email: ' + values['MPOS_ADMIN_EMAIL'])
    print('Initial password: ' + values['MPOS_ADMIN_PASSWORD'])
    print('If you changed the password in the app, use your updated password.')
