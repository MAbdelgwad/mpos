import React,{useState,useEffect,useRef} from 'react';
import {useSelector} from 'react-redux';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
const fresh=()=>({amount:'',date:today(),method:'CASH',reference:'',notes:''});
export default function PaymentPanel({type,document,onChanged}) {
 const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar',t=(a,e)=>ar?a:e;
 const permissions=useSelector(s=>s.config)||[],allowed=permissions.includes('manage_payments');
 const [rows,setRows]=useState([]),[summary,setSummary]=useState(null),[form,setForm]=useState(fresh),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const key=useRef(crypto.randomUUID()),pending=useRef(false);
 const path=`${type}/${document.id}/payments`;
 const message=e=>e.response?.data?.message||t('تعذر إتمام الطلب. أعد المحاولة.','Request failed. Please retry.');
 const load=async()=>{const r=await api.get(path);setRows(r.data.data);setSummary(r.data.summary);};
 useEffect(()=>{let active=true;if(allowed)api.get(path).then(r=>{if(active){setRows(r.data.data);setSummary(r.data.summary);}}).catch(e=>{if(active)setError(message(e));});return()=>{active=false;};},[path,allowed]);
 async function save(e){e.preventDefault();if(pending.current)return;pending.current=true;setBusy(true);setError('');try{await api.post(path,form,{headers:{'Idempotency-Key':key.current}});key.current=crypto.randomUUID();setForm(fresh());await load();await onChanged();}catch(e){setError(message(e));}finally{pending.current=false;setBusy(false);}}
 async function reverse(row){if(pending.current)return;const reason=window.prompt(t('سبب عكس الدفعة؟ تأكد من معالجة أي حركة مالية فعلية خارج التطبيق.','Reason for voiding? Reconcile any actual funds outside this application.'));if(!reason?.trim())return;pending.current=true;setBusy(true);setError('');try{await api.post(`${path}/${row.id}/void`,{reason});await load();await onChanged();}catch(e){setError(message(e));}finally{pending.current=false;setBusy(false);}}
 if(!allowed)return null;
 return <section className="border-top pt-3 mt-3"><h4>{type==='sales'?t('تحصيل المبيعات','Sale collections'):t('سداد المورد','Supplier payments')}</h4>
  <p>{t('تسجيل دفعات منفذة خارج التطبيق؛ اختيار البطاقة أو البنك لا ينفذ تحويلًا أو خصمًا فعليًا. عكس الدفعة يصحح السجل ولا ينفذ رد أموال.','Record payments made outside the app. Card/bank selection does not move money. Voiding corrects this record; it does not issue a refund.')}</p>
  {error&&<div className="alert alert-danger" role="alert">{error}</div>}
  {summary&&<p>{t('المدفوع','Paid')}: <strong>{summary.paid}</strong> — {t('المتبقي','Due')}: <strong>{summary.due}</strong></p>}
  {document.attributes.status===1&&summary&&Number(summary.due)>0&&<form onSubmit={save}><fieldset disabled={busy}><div className="row">
   <label className="col-md-3">{t('المبلغ','Amount')}<input required type="number" min="0.0001" step="0.0001" max={summary.due} className="form-control" value={form.amount} onChange={e=>setForm({...form,amount:e.target.value})}/></label>
   <label className="col-md-3">{t('التاريخ','Date')}<input required type="date" className="form-control" value={form.date} onChange={e=>setForm({...form,date:e.target.value})}/></label>
   <label className="col-md-3">{t('الطريقة','Method')}<select className="form-select" value={form.method} onChange={e=>setForm({...form,method:e.target.value})}><option value="CASH">{t('نقدي','Cash')}</option><option value="BANK">{t('تحويل بنكي','Bank transfer')}</option><option value="CARD">{t('بطاقة','Card')}</option></select></label>
   <label className="col-md-3">{t('مرجع الدفع','Payment reference')}<input maxLength={120} className="form-control" value={form.reference} onChange={e=>setForm({...form,reference:e.target.value})}/></label>
  </div><label className="d-block my-2">{t('ملاحظات','Notes')}<input maxLength={1000} className="form-control" value={form.notes} onChange={e=>setForm({...form,notes:e.target.value})}/></label><button type="submit" className="btn btn-primary my-2">{t('تسجيل الدفعة','Record payment')}</button></fieldset></form>}
  <div className="table-responsive"><table className="table"><thead><tr>{[t('الرقم','ID'),t('التاريخ','Date'),t('المبلغ','Amount'),t('الطريقة','Method'),t('المرجع','Reference'),t('الحالة / السبب','Status / reason')].map(x=><th key={x}>{x}</th>)}</tr></thead><tbody>{rows.map(row=><tr key={row.id}><td>{row.id}</td><td>{String(row.attributes.date).slice(0,10)}</td><td>{row.attributes.amount}</td><td>{row.attributes.method}</td><td>{row.attributes.reference}</td><td>{row.attributes.voided_at?<span>{t('معكوسة','Voided')}: {row.attributes.void_reason}</span>:<button type="button" disabled={busy} className="btn btn-sm btn-outline-danger" onClick={()=>reverse(row)}>{t('عكس الدفعة','Void payment')}</button>}</td></tr>)}</tbody></table></div>
 </section>;
}
