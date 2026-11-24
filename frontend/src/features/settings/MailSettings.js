import React,{useState,useEffect} from 'react';
import api from '../../config/apiConfig';
export default function MailSettings({ar}){
 const t=(a,e)=>ar?a:e,[v,setV]=useState(null),[msg,setMsg]=useState(''),[busy,setBusy]=useState(false),[to,setTo]=useState('');
 useEffect(()=>{api.get('mail-settings').then(r=>setV({...r.data.data,password:''})).catch(e=>setMsg(e.response?.data?.message||'Request failed'));},[]);
 async function run(action){setBusy(true);setMsg('');try{await action();setMsg(t('تم بنجاح','Completed'));}catch(e){setMsg(e.response?.data?.message||t('تعذر إتمام الطلب','Request failed'));}finally{setBusy(false);}}
 const change=e=>setV({...v,[e.target.name]:e.target.type==='checkbox'?e.target.checked:e.target.value});
 if(!v)return <p role="status">{msg}</p>;
 return <section className="card p-5 mt-5" dir={ar?'rtl':'ltr'}><h2>{t('البريد الإلكتروني ومشاركة الفواتير','Email and invoice sharing')}</h2><p role="status">{msg}</p>
 <form onSubmit={e=>{e.preventDefault();run(async()=>{const r=await api.put('mail-settings',v);setV({...r.data.data,password:''});});}}>
 {Object.entries({host:t('خادم SMTP','SMTP host'),port:t('المنفذ','Port'),username:t('اسم المستخدم','Username'),password:t('كلمة مرور التطبيق — اتركها فارغة للإبقاء على الحالية','App password — leave blank to keep current'),from_email:t('بريد المرسل','Sender email'),from_name:t('اسم المرسل','Sender name'),public_url:t('عنوان الموقع الذي يستطيع المستلم فتحه','Public application URL')}).map(([k,l])=><label className="d-block mb-3" key={k}>{l}<input className="form-control" name={k} type={k==='password'?'password':k==='port'?'number':k==='from_email'?'email':'text'} value={v[k]} onChange={change} autoComplete={k==='password'?'new-password':'off'} maxLength={k==='password'?1024:500}/></label>)}
 <p>{t('روابط localhost تعمل على جهازك فقط. للمشاركة الخارجية استخدم نطاقًا متاحًا للمستلم عبر HTTPS.','Localhost links work only on your computer. Use a reachable HTTPS domain for external sharing.')}</p>
 <label className="d-block mb-3">{t('تشفير الاتصال','Connection security')}<select className="form-select" name="security" value={v.security} onChange={change}><option value="starttls">STARTTLS (587)</option><option value="ssl">SSL/TLS (465)</option></select></label>
 <label className="d-block mb-3"><input type="checkbox" name="enabled" checked={v.enabled} onChange={change}/> {t('تفعيل إرسال البريد','Enable email')}</label>
 <label className="d-block mb-3"><input type="checkbox" name="verify_new_users" checked={v.verify_new_users} onChange={change}/> {t('إلزام الحسابات الجديدة بتأكيد البريد قبل الدخول','Require email verification for new accounts')}</label>
 <p>{t('الحسابات الحالية لن تتأثر. يُرسل رابط التأكيد عند إنشاء المستخدم بواسطة المسؤول، وصلاحيته 24 ساعة.','Existing accounts are unaffected. An administrator-created user receives a verification link valid for 24 hours.')}</p>
 <button className="btn btn-primary" disabled={busy}>{t('حفظ إعدادات البريد','Save email settings')}</button></form>
 <form className="mt-4" onSubmit={e=>{e.preventDefault();run(()=>api.post('mail-settings/test',{email:to}));}}><label>{t('إرسال رسالة اختبار باستخدام الإعدادات المحفوظة','Send test using saved settings')}<input className="form-control" type="email" required value={to} onChange={e=>setTo(e.target.value)}/></label><button className="btn btn-outline-primary m-2" disabled={busy}>{t('إرسال الاختبار','Send test')}</button></form>

 </section>;
}
