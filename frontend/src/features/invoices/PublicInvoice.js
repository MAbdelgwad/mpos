import React,{useEffect,useState,useRef} from 'react';
import {useParams} from 'react-router-dom';
import axios from 'axios';
import {invoiceHtml} from './InvoicePrintButton';
export default function PublicInvoice(){
 const {token}=useParams(),[html,setHtml]=useState(''),[error,setError]=useState(''),[ready,setReady]=useState(false),frame=useRef(null);
 useEffect(()=>{let active=true;setHtml('');setError('');axios.post('/api/public/invoices/view',{token}).then(r=>{if(active)setHtml(invoiceHtml(r.data.document,r.data.type,r.data.settings));}).catch(()=>{if(active)setError('الرابط غير صالح أو انتهت صلاحيته أو تم إلغاؤه — Invalid, expired or revoked link');});return()=>{active=false;};},[token]);
 return <main style={{padding:16}}><h1>عرض الفاتورة / Invoice view</h1><p>نسخة ثابتة وقت المشاركة — Snapshot at sharing time</p><p role="alert">{error}</p>{!html&&!error&&<p>جارٍ التحميل / Loading</p>}{html&&<><button disabled={!ready} onClick={()=>{frame.current.contentWindow.focus();frame.current.contentWindow.print();}}>طباعة / حفظ PDF — Print / save PDF</button><iframe ref={frame} title="الفاتورة / Invoice" srcDoc={html} referrerPolicy="no-referrer" onLoad={()=>setReady(true)} style={{width:'100%',height:'85vh',border:0}}/></>}</main>;
}
