#!/usr/bin/env python3
import json
from pathlib import Path
import sys
import time
import urllib.request
import urllib.error

env = Path(__file__).resolve().parent.parent / '.env'
if not env.exists():
    raise SystemExit('Run bash scripts/start.sh first.')
values = dict(line.split('=', 1) for line in env.read_text().splitlines() if line and not line.startswith('#'))
base = 'http://127.0.0.1:' + values.get('MPOS_WEB_PORT', '18181')

def request(path, payload=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, data=None if payload is None else json.dumps(payload).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=5) as response:
        return response.read()

attempts = 60 if '--wait' in sys.argv else 1
for attempt in range(attempts):
    try:
        assert json.loads(request('/api/health'))['status'] == 'UP'
        break
    except (OSError, ValueError, AssertionError):
        if attempt + 1 == attempts:
            raise SystemExit('Health check failed. Run bash scripts/logs.sh for details.')
        time.sleep(3)
try:
    page = request('/').decode()
    assert '<html' in page.lower(), 'Frontend HTML missing'
    login = json.loads(request('/api/login', {'email': values['MPOS_ADMIN_EMAIL'], 'password': values['MPOS_ADMIN_PASSWORD']}))
    token = login['data']['token']
    try:
        for endpoint in ['/api/config', '/api/products', '/api/warehouses', '/api/purchases', '/api/sales', '/api/inventory/balances', '/api/inventory/movements']:
            json.loads(request(endpoint, token=token))
            print('PASS ' + endpoint)
        for resource in ['base-units', 'units', 'product-categories', 'brands', 'products', 'warehouses', 'suppliers', 'customers', 'users', 'roles', 'purchases', 'sales']:
            endpoint = '/api/' + resource + '?sort=-created_at&page%5Bsize%5D=10&page%5Bnumber%5D=1'
            json.loads(request(endpoint, token=token))
            print('PASS paginated ' + resource)
        json.loads(request('/api/base-units?page%5Bsize%5D=0', token=token))
        print('PASS base-unit dropdown')
    finally:
        request('/api/logout', {}, token)
except (OSError, ValueError, KeyError, AssertionError) as error:
    raise SystemExit('Verification failed: ' + str(error) + '\nIf you changed the admin password in the app, update MPOS_ADMIN_PASSWORD in .env to verify login.')
print('PASS: frontend HTML, proxied API, login and protected reads. No business records created.')
print('Browser interaction and business acceptance tests must still be performed manually.')
