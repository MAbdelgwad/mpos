import React from 'react';

export function invoiceOptions(settings = {}) {
    const choice = (key, values, fallback) => values.includes(settings[key]) ? settings[key] : fallback;
    return {
        paper: choice('invoice_paper', ['A4', 'A5', '80mm'], 'A4'),
        template: choice('invoice_template', ['classic', 'modern', 'compact'], 'classic'),
        language: choice('invoice_language', ['ar', 'en'], 'ar'),
        color: /^#[0-9a-fA-F]{6}$/.test(settings.invoice_color || '') ? settings.invoice_color : '#243b53',
        font: choice('invoice_font_size', ['10','11','12','13','14','15','16','17','18'], '12'),
        code: settings.invoice_show_code !== 'false', notes: settings.invoice_show_notes !== 'false',
        payments: settings.invoice_show_payments !== 'false', signature: settings.invoice_show_signature === 'true'
    };
}
export function invoiceCss(settings) {
    const o = invoiceOptions(settings), narrow = o.paper === '80mm';
    return `@page{size:${narrow ? 'auto' : o.paper};margin:${narrow ? '3mm' : '12mm'}}
    *{box-sizing:border-box}body{margin:0;background:white;color:#111;font-family:Arial,Tahoma,sans-serif;font-size:${o.font}px;line-height:1.5}
    .invoice{max-width:${narrow ? '74mm' : o.paper === 'A5' ? '124mm' : '186mm'};margin:0 auto;padding:8px;overflow-wrap:anywhere}
    header{border-bottom:3px solid ${o.color};margin-bottom:18px;padding-bottom:10px}h1,h2{margin:4px 0;color:${o.color}}
    h1{font-size:1.6em}h2{font-size:1.25em}.modern header{background:${o.color};padding:12px;color:white}.modern header h1,.modern header h2{color:white}
    .compact td,.compact th{padding:3px}.metadata{display:flex;flex-wrap:wrap;gap:8px 24px;margin-bottom:14px}.status{font-weight:bold;border:2px solid ${o.color};padding:6px;margin:10px 0}
    table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{padding:6px;border-bottom:1px solid #ccc;text-align:start;vertical-align:top}th{color:${o.color};background:#f0f2f4}thead{display:table-header-group}tr{break-inside:avoid}
    .totals{margin-top:18px;margin-inline-start:auto;max-width:${narrow ? '100%' : '85mm'}}.totals div{display:flex;justify-content:space-between;gap:12px;padding:4px}.grand{font-size:1.2em;font-weight:bold;border-top:2px solid ${o.color}}
    .notes,footer{white-space:pre-wrap;margin-top:20px}.signature{display:flex;justify-content:space-between;margin-top:36px;break-inside:avoid}footer{border-top:1px solid #ccc;padding-top:8px}
    .receipt-line{border-bottom:1px dashed #aaa;padding:8px 0;break-inside:avoid}.number{direction:ltr;unicode-bidi:isolate;display:inline-block}
    @media print{body{print-color-adjust:exact;-webkit-print-color-adjust:exact}.invoice{padding:0}}`;
}
const amount = value => value === null || value === undefined ? '—' : String(value);
export default function InvoiceDocument({document: doc, type, settings = {}}) {
    const a = doc.attributes, o = invoiceOptions(settings), ar = o.language === 'ar', t = (a,e) => ar ? a : e;
    const sales = type === 'sales', lines = a[sales ? 'sale_items' : 'purchase_items'] || [];
    const money = value => <span className="number">{amount(value)} {a.currency_symbol !== undefined ? a.currency_symbol : (settings.currency_symbol || '')}</span>;
    const state = Number(a.status), status = ({1:sales?t('مكتمل','Completed'):t('مستلم','Received'),2:t('معلق — غير مرحّل','Pending — not posted'),3:t('طلب — غير مرحّل','Order — not posted'),4:t('ملغى','Cancelled')})[state] || t('غير معروف','Unknown');
    return <article className={`invoice ${o.template}`} dir={ar?'rtl':'ltr'} lang={o.language}>
        <header><h1>{settings.company_name || 'MPOS'}</h1><h2>{sales?t('فاتورة مبيعات','Sales invoice'):t('مستند مشتريات','Purchase document')}</h2>
            {settings.invoice_address && <div>{settings.invoice_address}</div>}{settings.invoice_phone && <div>{t('الهاتف','Phone')}: {settings.invoice_phone}</div>}
            {settings.invoice_tax_number && <div>{t('الرقم الضريبي للشركة','Company tax number')}: {settings.invoice_tax_number}</div>}
        </header>
        {a.import_containers?.length>0&&<section><strong>{t('الشحنة / الكونتينرات','Shipment / containers')}</strong>{a.import_containers.map((c,i)=><div key={i}>{c.booking_number} — {c.container_number}: {c.pieces} {t('قطعة','pieces')}</div>)}</section>}
        <div className="metadata"><span>{t('الرقم','Reference')}: {a.reference_code}</span><span>{t('التاريخ','Date')}: {String(a.date || '').slice(0,10)}</span></div>
        <div>{sales?t('العميل','Customer'):t('المورد','Supplier')}: {sales?a.customer_name:a.supplier_name}</div>
        <div>{t('المخزن','Warehouse')}: {a.warehouse_name}</div><div className="status">{status}</div>
        {o.paper === '80mm' ? lines.map((x,i)=><div className="receipt-line" key={x.id || i}>
            <strong>{x.name}</strong>{o.code && <div>{x.code}</div>}
            <div>{t('الكمية','Quantity')}: {amount(x.quantity)} {x.unit_name || ''} × {money(x[sales?'product_price':'product_cost'])}</div>
            <div>{t('خصم الصنف','Item discount')}: {money(x.discount_amount)} — {t('ضريبة الصنف','Item tax')}: {money(x.tax_amount)}</div>
            <div>{t('الإجمالي شامل ضريبة الصنف','Total incl. item tax')}: {money(x.sub_total)}</div>
        </div>) : <table><thead><tr>{[t('الصنف','Item'),t('الكمية','Qty'),t('سعر الوحدة','Unit price'),t('الخصم','Discount'),t('الضريبة','Tax'),t('الإجمالي','Total')].map(x=><th key={x}>{x}</th>)}</tr></thead>
            <tbody>{lines.map((x,i)=><tr key={x.id || i}><td>{x.name}{o.code && <div>{x.code}</div>}</td><td>{amount(x.quantity)}<div>{x.unit_name || ''}</div></td><td>{money(x[sales?'product_price':'product_cost'])}</td><td>{money(x.discount_amount)}</td><td>{money(x.tax_amount)}<div>{amount(x.tax_value)}% {Number(x.tax_type)===2?t('شاملة','Inclusive'):t('مضافة','Exclusive')}</div></td><td>{money(x.sub_total)}</td></tr>)}</tbody></table>}
        <div className="totals"><div><span>{t('خصم الفاتورة','Invoice discount')}</span>{money(a.discount)}</div>
            <div><span>{t('ضريبة الفاتورة الإضافية','Additional invoice tax')} ({amount(a.tax_rate)}%)</span>{money(a.tax_amount)}</div>
            <div><span>{t('الشحن','Shipping')}</span>{money(a.shipping)}</div>
            <div className="grand"><span>{t('الإجمالي النهائي','Grand total')}</span>{money(a.grand_total)}</div>
            {o.payments && <><div><span>{t('المدفوع','Paid')}</span>{money(a.paid_amount)}</div><div><span>{t('المتبقي','Due')}</span>{money(a.due_amount)}</div></>}
        </div>
        {o.notes && a.notes && <section className="notes">{t('ملاحظات','Notes')}: {a.notes}</section>}
        {o.signature && <div className="signature"><span>{t('توقيع المستلم','Recipient signature')}: __________</span><span>{t('التوقيع والختم','Signature / stamp')}: __________</span></div>}
        {settings.invoice_footer && <footer>{settings.invoice_footer}</footer>}
    </article>;
}
