import React,{useState} from 'react';
import {useParams,Link} from 'react-router-dom';
import axios from 'axios';
export default function VerifyEmail(){const {token}=useParams(),[msg,setMsg]=useState(''),[busy,setBusy]=useState(false),[done,setDone]=useState(false);
 async function confirm(){setBusy(true);try{await axios.post('/api/public/verify-email',{token});setDone(true);setMsg('تم تأكيد البريد. يمكنك تسجيل الدخول — Email verified. You can now log in.');}catch(e){setMsg('الرابط غير صالح أو منتهي أو سبق استخدامه. اطلب من المسؤول إعادة الإرسال — Invalid, expired or used link. Ask your administrator to resend.');}finally{setBusy(false);}}
 return <main className="container p-5"><h1>تأكيد البريد الإلكتروني / Verify email</h1><p role="status">{msg}</p>{!done&&<button className="btn btn-primary" disabled={busy} onClick={confirm}>تأكيد / Confirm</button>} <Link to="/login">تسجيل الدخول / Login</Link></main>;}
