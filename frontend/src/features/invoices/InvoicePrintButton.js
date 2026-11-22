import InvoiceShareButton from './InvoiceShareButton';
import React,{useState,useRef,useEffect} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
import InvoiceDocument,{invoiceCss,invoiceOptions} from './InvoiceDocument';

export function invoiceHtml(document,type,settings) {
    const o=invoiceOptions(settings);
    return '<!doctype html>'+renderToStaticMarkup(<html lang={o.language} dir={o.language==='ar'?'rtl':'ltr'}><head><meta charSet="utf-8"/><title>{document.attributes.reference_code}</title><style>{invoiceCss(settings)}</style></head><body><InvoiceDocument document={document} type={type} settings={settings}/></body></html>);
}
export default function InvoicePrintButton({type,id,previewDocument,previewSettings}) {
    const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar',t=(a,e)=>ar?a:e;
    const [html,setHtml]=useState(''),[busy,setBusy]=useState(false),[ready,setReady]=useState(false),[error,setError]=useState('');
    const frame=useRef(null),mounted=useRef(true);
    useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
    async function preview(){
        setBusy(true);setError('');setReady(false);setHtml('');
        try {
            let document=previewDocument,settings=previewSettings;
            if(!document){const [d,s]=await Promise.all([api.get(`${type}/${id}`),api.get('front-setting')]);document=d.data.data;settings=s.data.data.value;}
            if(mounted.current)setHtml(invoiceHtml(document,type,settings));
        }catch(e){if(mounted.current)setError(e.response?.data?.message||t('تعذر تحميل الفاتورة','Unable to load invoice'));}
        finally{if(mounted.current)setBusy(false);}
    }
    function print(){try{frame.current.contentWindow.focus();frame.current.contentWindow.print();}catch(e){setError(t('تعذر فتح نافذة الطباعة. أعد المعاينة وحاول مجددًا.','Unable to open print dialog. Preview again and retry.'));}}
    return <span>{!previewDocument&&<InvoiceShareButton type={type} id={id}/>}<button type="button" className="btn btn-outline-primary m-1" disabled={busy} onClick={preview}>{busy?t('جارٍ التحميل','Loading'):t('معاينة / طباعة الفاتورة','Preview / print invoice')}</button>
        {error&&<span className="text-danger" role="alert">{error}</span>}
        {html&&<div role="dialog" aria-modal="true" aria-label={t('معاينة الفاتورة','Invoice preview')} style={{position:'fixed',inset:0,zIndex:2000,background:'#eef1f5',padding:16,display:'flex',flexDirection:'column'}}>
            <div><button type="button" className="btn btn-primary m-1" disabled={!ready} onClick={print}>{t('طباعة / حفظ PDF','Print / save PDF')}</button><button type="button" className="btn btn-secondary m-1" onClick={()=>{setHtml('');setReady(false);}}>{t('إغلاق','Close')}</button>
                <span>{t('اختر الطابعة أو Save as PDF. للورق الحراري اختر عرض 80 مم من إعدادات الطابعة.','Choose a printer or Save as PDF. For thermal paper select 80 mm in printer settings.')}</span></div>
            <iframe ref={frame} title={t('الفاتورة','Invoice')} srcDoc={html} onLoad={()=>setReady(true)} style={{border:0,background:'white',width:'100%',flex:1}}/>
        </div>}
    </span>;
}
