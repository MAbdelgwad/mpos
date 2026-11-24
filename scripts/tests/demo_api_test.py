"""Integration test: use a fresh disposable API; creates fixtures and a control record."""
import importlib.util
import json
import os
from decimal import Decimal
from pathlib import Path
spec=importlib.util.spec_from_file_location('demo_seed',Path(__file__).resolve().parents[1]/'seed-demo.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
c=m.Client(os.environ['DEMO_TEST_URL']);c.token=c.call('POST','login',{'email':os.environ['MPOS_DEMO_EMAIL'],'password':os.environ['MPOS_DEMO_PASSWORD']})['data']['token']
try:
    baseline=c.call('POST','brands',{'name':'Unrelated existing brand'})['data']['id']
    original=c.call;fault=[False]
    def fail_after_commit(method,path,body=None,key=None):
        result=original(method,path,body,key)
        if method=='POST' and path=='sales' and not fault[0]:
            fault[0]=True;raise RuntimeError('Simulated lost sale response')
        return result
    c.call=fail_after_commit
    try: m.Seeder(c).run();raise AssertionError('Fault was not injected')
    except RuntimeError as error: assert str(error)=='Simulated lost sale response'
    c.call=original
    seeder=m.Seeder(c);ids,docs=seeder.run()
    assert len(c.all('products'))==12
    assert len(c.all('purchases'))==4 and len(c.all('sales'))==5
    balances=c.call('GET','inventory/balances?page%5Bsize%5D=100')['data']
    for wi,start in [(0,50),(1,25)]:
        for pi,product in enumerate(ids['products']):
            sold=sum(Decimal(str(qty)) for doc in m.DATA['sales'] if doc['status']==1 and doc['warehouse']==wi for idx,qty in doc['items'] if idx==pi)
            row=next(x for x in balances if x['warehouse_id']==ids['warehouses'][wi] and x['product_id']==product)
            assert Decimal(str(row['quantity']))==Decimal(start)-sold,(wi,pi,row)
    payment_count=0
    for resource in ('purchases','sales'):
        for spec in m.DATA[resource]:
            doc=c.call('GET',resource+'/'+str(docs[spec['ref']]))['data']['attributes']
            expected_total=sum(Decimal(str(m.DATA['products'][i]['price' if resource=='sales' else 'cost']))*Decimal(str(q))*Decimal('1.15') for i,q in spec['items'])
            assert Decimal(str(doc['grand_total']))==expected_total,(spec['ref'],doc['grand_total'],expected_total)
            expected={ 'full':1,'partial':3,'none':2 }[spec['payment']]
            assert doc['payment_status']==expected,(spec['ref'],doc)
            payments=c.call('GET',resource+'/'+str(docs[spec['ref']])+'/payments')['data'];payment_count+=len(payments)
    assert payment_count==4
    ledger=c.call('GET','inventory/movements?page%5Bsize%5D=100')['data']
    repeat=m.Seeder(c);repeat.run();assert repeat.created==0
    assert c.call('GET','inventory/balances?page%5Bsize%5D=100')['data']==balances
    assert c.call('GET','inventory/movements?page%5Bsize%5D=100')['data']==ledger
    assert any(x['id']==baseline and x['attributes']['name']=='Unrelated existing brand' for x in c.all('brands'))
    print(json.dumps({'products':12,'purchases':4,'sales':5,'payments':4,'balances_checked':24,'movements':len(ledger),'resume_after_lost_response':'passed','repeat_without_duplicates':'passed','existing_data_preserved':'passed'}))
finally: c.call('POST','logout',{})
