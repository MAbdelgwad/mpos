import {downloadDiagnostics} from '../shared/diagnostics/diagnostics';
import React, {useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {useDispatch,useSelector} from 'react-redux';
import {logoutAction} from '../store/action/authAction';
import {Tokens} from '../constants';
import api from '../config/apiConfig';
export default function SpringHeader() {
    const navigate=useNavigate(),dispatch=useDispatch();
    const permissions=useSelector(s=>s.config)||[];
    const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar';
    const [error,setError]=useState(''),[savingLanguage,setSavingLanguage]=useState(false);
    async function language(e) {
        const nextLanguage=e.target.value;
        if(savingLanguage)return;
        setSavingLanguage(true);setError('');
        try {await api.post('change-language',{language:nextLanguage});localStorage.setItem(Tokens.UPDATED_LANGUAGE,nextLanguage);localStorage.setItem(Tokens.LANGUAGE,nextLanguage);window.location.reload();}
        catch(error){setError(error.response?.data?.message || error.message || 'Unable to change language');}
        finally {setSavingLanguage(false);}
    }
    return <div className="d-flex align-items-center gap-3"><Link to="/app/dashboard">MPOS</Link>
        {permissions.includes('manage_sale')&&<Link to="/app/sales">{ar?'المبيعات':'Sales'}</Link>}
        {permissions.includes('manage_purchase')&&<Link to="/app/purchases">{ar?'المشتريات':'Purchases'}</Link>}
        {permissions.includes('manage_inventory')&&<Link to="/app/inventory">{ar?'المخزون':'Inventory'}</Link>}
        <select disabled={savingLanguage} aria-label="Language" className="form-select" value={ar?'ar':'en'} onChange={language}><option value="en">English</option><option value="ar">العربية</option></select>
        <button className="btn btn-outline-primary text-nowrap" onClick={()=>dispatch(logoutAction({},navigate))}>{ar?'تسجيل الخروج':'Sign out'}</button>
        <button className="btn btn-outline-secondary text-nowrap" onClick={downloadDiagnostics}>{ar?'تقرير الأخطاء':'Diagnostics'}</button>
        {error && <span role="alert">{error}</span>}</div>;
}
