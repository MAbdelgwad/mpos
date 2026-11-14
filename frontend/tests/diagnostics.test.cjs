const test = require('node:test');
const assert = require('node:assert/strict');
let storage = new Map();
global.sessionStorage = {getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value)};
global.window = {location: {hash: '#/app/products?password=secret-value', pathname: '/'}, addEventListener() {}};
const d = require('../src/shared/diagnostics/diagnostics');
test('reports omit tokens, headers, payloads and URL queries, retaining request correlation', () => {
    const id = 'f59b8a72-0700-4c3e-ad72-ec3c3c98a322';
    d.httpDiagnostic({status: 422, headers: {'x-request-id': id, authorization: 'Bearer private-token'},
        data: {password: 'body-secret'}, config: {baseURL: 'http://localhost:18181/api/', url: 'products?token=url-secret', method: 'post', data: {password: 'payload-secret'}}}, new Error('failure'));
    const report = d.report(), serialized = JSON.stringify(report);
    for (const secret of ['private-token', 'body-secret', 'payload-secret', 'url-secret', 'secret-value']) assert(!serialized.includes(secret));
    assert.equal(report.events.at(-1).request_id, id);
    assert.equal(report.events.at(-1).source, '/api/products');
    assert.equal(report.events.at(-1).status, 422);
});
test('buffer is bounded and works when browser storage is unavailable', () => {
    global.sessionStorage = {getItem() {throw new Error('blocked')}, setItem() {throw new Error('blocked')}};
    for (let i = 0; i < 80; i++) d.recordDiagnostic({kind: 'JAVASCRIPT_ERROR', message: 'sample ' + i});
    assert.equal(d.report().events.length, 50);
    assert.equal(d.report().events.at(-1).message, 'sample 79');
});
test('text diagnostics redact common credentials and strip stack URL query values', () => {
    const row = d.recordDiagnostic({kind: 'JAVASCRIPT_ERROR', message: 'password=my-secret user@example.com Bearer private-value',
        stack: 'Error: password=first-line-secret\n at f (http://localhost/js/app.js?token=stack-secret)'});
    const text = JSON.stringify(row);
    for(const secret of ['my-secret','user@example.com','private-value','first-line-secret','stack-secret']) assert(!text.includes(secret));
    assert(text.includes('/js/app.js'));
});
