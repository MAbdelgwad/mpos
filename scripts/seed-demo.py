#!/usr/bin/env python3
"""Load fictional MPOS fixtures through supported APIs. Never deletes or updates existing data."""
import argparse
import fcntl
import json
import os
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parent.parent
DATA = json.loads((ROOT / 'demo/catalog.json').read_text())
PREFIX = '[تجريبي] '

class Client:
    def __init__(self, base):
        self.base, self.token = base.rstrip('/'), None
    def call(self, method, path, body=None, key=None):
        headers = {'Content-Type':'application/json'}
        if self.token: headers['Authorization'] = 'Bearer ' + self.token
        if key: headers['Idempotency-Key'] = key
        request = urllib.request.Request(self.base + '/api/' + path, method=method, headers=headers,
            data=None if body is None else json.dumps(body, ensure_ascii=False).encode())
        try:
            with urllib.request.urlopen(request, timeout=30) as response: return json.load(response)
        except urllib.error.HTTPError as error:
            # Do not print request bodies, tokens, credentials or arbitrary server response text.
            raise RuntimeError(f'{method} {path}: HTTP {error.code}; review scripts/logs.sh api') from None
    def all(self, resource):
        if resource not in ('sales','purchases'):
            return self.call('GET',resource+'?page%5Bsize%5D=0')['data']
        rows=[];page=1
        while True:
            result=self.call('GET',resource+f'?page%5Bsize%5D=100&page%5Bnumber%5D={page}')
            rows.extend(result['data'])
            if len(rows)>=result['meta']['total']: return rows
            page+=1

class Seeder:
    def __init__(self, client): self.client=client;self.cache={};self.created=0;self.reused=0
    def ensure(self, resource, body, field='name'):
        if resource not in self.cache: self.cache[resource]=self.client.all(resource)
        rows=self.cache[resource]
        found=next((row for row in rows if row['attributes'].get(field)==body[field]),None)
        if found:
            # Abort on reused codes whose meaning was changed; never rewrite the user's edits.
            for key in ('name','product_unit','base_unit','product_category_id','brand_id'):
                if key in body and str(found['attributes'].get(key))!=str(body[key]):
                    raise RuntimeError(f'Existing demo record conflicts in {resource}: {body[field]}. No existing data changed.')
            self.reused+=1;return found['id']
        result=self.client.call('POST',resource,body)['data'];rows.append(result);self.created+=1;return result['id']
    def run(self):
        ids={}
        for resource in ('brands','product-categories'):
            names=DATA['brands' if resource=='brands' else 'categories']
            ids[resource]=[self.ensure(resource,{'name':PREFIX+n}) for n in names]
        ids['units']=[self.ensure('units',{'name':PREFIX+u['name'],'short_name':'D-'+u['short_name']}) for i,u in enumerate(DATA['units'])]
        ids['warehouses']=[self.ensure('warehouses',{'name':PREFIX+n,'phone':'0000000000','country':'Saudi Arabia','city':'Riyadh' if i==0 else 'Jeddah'}) for i,n in enumerate(DATA['warehouses'])]
        for resource in ('customers','suppliers'):
            ids[resource]=[self.ensure(resource,{'name':PREFIX+n,'email':f'mpos-demo-{resource}-{i+1}@example.com','phone':'0000000000','country':'Saudi Arabia','city':'Riyadh','address':'عنوان افتراضي للاختبار فقط'},'email') for i,n in enumerate(DATA[resource])]
        ids['products']=[]
        for p in DATA['products']:
            ids['products'].append(self.ensure('products',{'name':PREFIX+p['name'],'code':p['code'],
                'product_category_id':ids['product-categories'][p['category']], 'brand_id':ids['brands'][p['brand']],
                'product_cost':p['cost'],'product_price':p['price'],'product_unit':ids['units'][p['unit']],
                'sale_unit':ids['units'][p['unit']],'purchase_unit':ids['units'][p['unit']],
                'stock_alert':5,'barcode_symbol':1,'tax_type':1,'order_tax':15,
                'notes':'بيانات استرشادية - QR وباركود: /images/demo/index.html'},'code'))
        documents={}
        for resource in ('purchases','sales'):
            existing=self.client.all(resource)
            for spec in DATA[resource]:
                marker=DATA['version']+':'+spec['ref'];note='[تجريبي] '+marker
                found=next((row for row in existing if row['attributes'].get('notes')==note),None)
                if found:
                    self.reused+=1;documents[spec['ref']]=found['id']
                    # Resume missing payments after interruption, but never undo edited/cancelled documents.
                    if found['attributes']['status']==1: self.payment(resource,found,spec,marker)
                    continue
                is_sale=resource=='sales';lines=[]
                for index,qty in spec['items']:
                    p=DATA['products'][index]
                    lines.append({'product_id':ids['products'][index],'quantity':qty,
                        'sale_unit' if is_sale else 'purchase_unit':ids['units'][p['unit']],
                        'product_price' if is_sale else 'product_cost':p['price' if is_sale else 'cost'],
                        'discount_type':2,'discount_value':0,'tax_type':1,'tax_value':15})
                body={'customer_id' if is_sale else 'supplier_id':ids['customers' if is_sale else 'suppliers'][spec['party']],
                    'warehouse_id':ids['warehouses'][spec['warehouse']],'date':DATA['date'],'status':spec['status'],
                    'discount':0,'shipping':0,'tax_rate':0,'notes':note,'sale_items' if is_sale else 'purchase_items':lines}
                found=self.client.call('POST',resource,body,key=marker.replace(':','-'))['data']
                existing.append(found);self.created+=1;documents[spec['ref']]=found['id'];self.payment(resource,found,spec,marker)
        print(f'Demo ready: {len(ids["products"])} products; 4 purchases; 5 sales. Created: {self.created}; reused: {self.reused}.')
        print('Labels: '+self.client.base+'/images/demo/index.html')
        return ids,documents
    def payment(self,resource,document,spec,marker):
        if spec['payment']=='none': return
        path=f'{resource}/{document["id"]}/payments';reference=marker+'-PAY'
        rows=self.client.call('GET',path)['data']
        if any(row['attributes']['reference']==reference for row in rows): return
        # A document edited/paid manually after seeding is left untouched.
        if rows:
            print(f'Skipped payment for {spec["ref"]}: existing payment records.');return
        total=Decimal(str(document['attributes']['grand_total']))
        amount=(total if spec['payment']=='full' else total/2).quantize(Decimal('0.0001'),rounding=ROUND_HALF_UP)
        self.client.call('POST',path,{'amount':str(amount),'date':DATA['date'],'method':'CASH' if resource=='sales' else 'BANK',
            'reference':reference,'notes':'دفعة افتراضية للاختبار، لا تمثل تحويل أموال'},key=reference.replace(':','-'))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--base-url');args=parser.parse_args()
    env=ROOT/'.env'
    values=dict(line.split('=',1) for line in env.read_text().splitlines() if line and not line.startswith('#')) if env.exists() else {}
    email=os.getenv('MPOS_DEMO_EMAIL') or values.get('MPOS_ADMIN_EMAIL')
    password=os.getenv('MPOS_DEMO_PASSWORD') or values.get('MPOS_ADMIN_PASSWORD')
    if not email or not password: raise RuntimeError('Run scripts/start.sh first, or set MPOS_DEMO_EMAIL and MPOS_DEMO_PASSWORD.')
    client=Client(args.base_url or 'http://127.0.0.1:'+values.get('MPOS_WEB_PORT','18181'))
    # Serializes seed runs in this checkout without storing credentials or server state.
    with (ROOT/'.demo-seed.lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        client.token=client.call('POST','login',{'email':email,'password':password})['data']['token']
        try: Seeder(client).run()
        finally:
            try: client.call('POST','logout',{})
            except Exception: pass
if __name__=='__main__':
    try: main()
    except (RuntimeError,OSError,ValueError,KeyError) as error: raise SystemExit('Demo setup failed: '+str(error))
