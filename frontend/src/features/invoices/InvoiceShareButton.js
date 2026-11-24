import React,{useState} from 'react';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
export default function InvoiceShareButton({type,id}){
 const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar',t=(a,e)=>ar?a:e;
 const [open,setOpen]=useState(false),[list,setList]=useState([]),[url,setUrl]=useState(''),[days,setDays]=useState('7'),[email,setEmail]=useState(''),[msg,setMsg]=useState(''),[busy,setBusy]=useState(false);
 const base=`invoice-shares/${type}/${id}`;
 async function load(){const r=await api.get(base);setList(r.data.data);}
 async function run(f){setBusy(true);setMsg('');try{await f();}catch(e){setMsg(e.response?.data?.message||t('تعذر إتمام الطلب','Request failed'));}finally{setBusy(false);}}
 return <span><button type="button" className="btn btn-outline-secondary m-1" onClick={()=>{setOpen(true);run(load);}}>{t('مشاركة / إرسال الفاتورة','Share / email invoice')}</button>{open&&<div role="dialog" aria-modal="true" aria-label={t('مشاركة الفاتورة','Share invoice')} style={{position:'fixed',inset:'8%',overflow:'auto',zIndex:2100,background:'white',padding:24,boxShadow:'0 0 0 100vmax #0008'}} dir={ar?'rtl':'ltr'}>
 <h2>{t('مشاركة الفاتورة للعرض فقط','Read-only invoice sharing')}</h2><p>{t('الرابط يعرض نسخة ثابتة وقت إنشائه لأي شخص يملك الرابط. أنشئ رابطًا جديدًا بعد تعديل الفاتورة. لا تُشارك الملاحظات الداخلية.','Anyone with the link can view a snapshot taken now. Create a new link after invoice changes. Internal notes are excluded.')}</p>
 <p role="status">{msg}</p><form onSubmit={e=>{e.preventDefault();run(async()=>{setUrl('');const r=await api.post(base,{days,email});setUrl(r.data.data.url);setMsg(email?t('تم إنشاء الرابط وإرساله بالبريد','Link created and emailed'):t('تم إنشاء الرابط','Link created'));await load();});}}>
 <label className="d-block">{t('صلاحية الرابط بالأيام (1–30)','Expiry in days (1–30)')}<input className="form-control" type="number" min="1" max="30" required value={days} onChange={e=>setDays(e.target.value)}/></label>
 <label className="d-block">{t('بريد المستلم — اختياري','Recipient email — optional')}<input className="form-control" type="email" value={email} onChange={e=>setEmail(e.target.value)}/></label><button className="btn btn-primary mt-3" disabled={busy}>{email?t('إنشاء الرابط وإرساله','Create and email link'):t('إنشاء رابط','Create link')}</button></form>
 {url&&<label className="d-block mt-3">{t('انسخ الرابط — يظهر مرة واحدة','Copy link — shown once')}<input className="form-control" readOnly value={url} onFocus={e=>e.target.select()}/></label>}
 <h3 className="mt-4">{t('الروابط السابقة','Previous links')}</h3>{list.map(x=><div key={x.id} className="border p-2">{t('ينتهي','Expires')}: {x.expires_at} — {x.revoked?t('ملغى','Revoked'):t('يمكن إلغاؤه','Revocable')} <button type="button" className="btn btn-sm btn-outline-danger" disabled={busy||x.revoked} onClick={()=>run(async()=>{await api.delete(`${base}/${x.id}`);await load();})}>{t('إلغاء الرابط','Revoke')}</button></div>)}
 <button type="button" className="btn btn-secondary mt-3" onClick={()=>{setOpen(false);setUrl('');}}>{t('إغلاق','Close')}</button></div>}</span>;
}
