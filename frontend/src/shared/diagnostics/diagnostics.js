/* Bounded, browser-local diagnostics. No request/response bodies, headers, tokens or app state. */
const KEY = 'mpos.diagnostics.v1';
const LIMIT = 50;
let memory = [];
let installed = false;

function sanitizeText(value, limit = 320) {
    return String(value || '')
        .replace(/Bearer\s+[^\s"']+/gi, 'Bearer [REDACTED]')
        .replace(/((?:password|token|secret|authorization|cookie)["']?\s*[:=]\s*)[^\s,;]+/gi, '$1[REDACTED]')
        .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[EMAIL]')
        .replace(/\b[A-Za-z0-9_-]{32,}\b/g, '[LONG_VALUE]')
        .replace(/[\r\n\t]+/g, ' ').slice(0, limit);
}
function safePath(value) {
    try { return new URL(String(value || ''), 'http://mpos.local/').pathname.replace(/(\/(?:shared-invoice|verify-email)\/)[^/]+/g, '$1[REDACTED]').slice(0, 240); }
    catch (_) { return '[invalid path]'; }
}
function safeStack(value) {
    // Keep code locations but drop the first line (which contains the exception message).
    return String(value || '').split('\n').slice(1, 9).map(line =>
        sanitizeText(line.replace(/https?:\/\/[^\s)]+/g, url => safePath(url)), 160)
    ).join('\n');
}
function pagePath() {
    if (typeof window === 'undefined') return '/';
    return safePath((window.location.hash || '').replace(/^#/, '') || window.location.pathname);
}
function events() {
    try { const parsed = JSON.parse(sessionStorage.getItem(KEY) || '[]'); if (Array.isArray(parsed)) memory = parsed.slice(-LIMIT); }
    catch (_) { /* Browsers can deny storage; logging must not break the app. */ }
    return memory;
}
function recordDiagnostic(input) {
    try {
        const row = {
            time: new Date().toISOString(), kind: sanitizeText(input.kind, 40), page: pagePath(),
            message: sanitizeText(input.message), source: safePath(input.source || '/'),
            stack: safeStack(input.stack), status: Number(input.status) || 0,
            method: /^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$/i.test(input.method || '') ? input.method.toUpperCase() : '',
            request_id: /^[a-f0-9-]{36}$/i.test(input.requestId || '') ? input.requestId : '',
            line: Number(input.line) || 0, column: Number(input.column) || 0
        };
        memory = [...events(), row].slice(-LIMIT);
        try { sessionStorage.setItem(KEY, JSON.stringify(memory)); } catch (_) { /* Memory fallback. */ }
        return row;
    } catch (_) { return null; }
}
function httpDiagnostic(response, error) {
    const config = response?.config || error?.config || {};
    const path = new URL(config.url || '/', config.baseURL || 'http://mpos.local/').pathname;
    return recordDiagnostic({kind: error ? 'HTTP_ERROR' : 'HTTP_RESPONSE', source: path,
        status: response?.status || 0, method: config.method,
        message: error ? (response ? 'HTTP request failed' : 'Network request failed') : '',
        requestId: response?.headers?.['x-request-id']});
}
function installDiagnostics() {
    if (installed || typeof window === 'undefined') return;
    installed = true;
    window.addEventListener('error', event => {
        if (event.target && event.target !== window) {
            recordDiagnostic({kind: 'RESOURCE_ERROR', source: event.target.src || event.target.href, message: 'Resource failed to load'});
        } else recordDiagnostic({kind: 'JAVASCRIPT_ERROR', message: event.message, stack: event.error?.stack,
            source: event.filename, line: event.lineno, column: event.colno});
    }, true);
    window.addEventListener('unhandledrejection', event => recordDiagnostic({kind: 'PROMISE_ERROR',
        message: event.reason instanceof Error ? event.reason.message : typeof event.reason === 'string' ? event.reason : 'Promise rejected',
        stack: event.reason?.stack}));
    window.downloadMposDiagnostics = downloadDiagnostics;
}
function report() {
    // Re-project stored data to prevent accidental inclusion of unrelated sessionStorage state.
    const rows = events().map(row => ({time: sanitizeText(row.time, 40), kind: sanitizeText(row.kind, 40),
        page: safePath(row.page), source: safePath(row.source), message: sanitizeText(row.message),
        stack: sanitizeText(row.stack, 1280), status: Number(row.status) || 0,
        method: sanitizeText(row.method, 10), request_id: /^[a-f0-9-]{36}$/i.test(row.request_id || '') ? row.request_id : '',
        line: Number(row.line) || 0, column: Number(row.column) || 0}));
    return {app: 'mpos', schema_version: 1, created_at: new Date().toISOString(), page: pagePath(), events: rows};
}
function downloadDiagnostics() {
    const blob = new Blob([JSON.stringify(report(), null, 2)], {type: 'application/json'});
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = 'mpos-browser-diagnostics.json'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
module.exports = {sanitizeText, safePath, recordDiagnostic, httpDiagnostic, installDiagnostics, report, downloadDiagnostics};
