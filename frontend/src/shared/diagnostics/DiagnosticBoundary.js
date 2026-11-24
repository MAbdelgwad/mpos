import React from 'react';
import {recordDiagnostic, downloadDiagnostics} from './diagnostics';

export default class DiagnosticBoundary extends React.Component {
    state = {failed: false};
    static getDerivedStateFromError() { return {failed: true}; }
    componentDidCatch(error, info) {
        recordDiagnostic({kind: 'REACT_RENDER_ERROR', message: error.message,
            stack: error.stack || info.componentStack});
    }
    render() {
        if (!this.state.failed) return this.props.children;
        return <main dir="rtl" style={{padding: 32, fontFamily: 'sans-serif'}}>
            <h2>تعذّر عرض الصفحة / Unable to display this page</h2>
            <p>نزّل تقرير الخطأ ثم أرسله للمراجعة. / Download the error report for review.</p>
            <button onClick={downloadDiagnostics}>تنزيل تقرير الأخطاء / Download diagnostics</button>{' '}
            <button onClick={() => window.location.reload()}>إعادة المحاولة / Retry</button>
        </main>;
    }
}
