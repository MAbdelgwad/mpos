import React, {useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {useDispatch,useSelector} from 'react-redux';
import {logoutAction} from '../../store/action/authAction';
import {Tokens} from '../../constants';
import api from '../../config/apiConfig';
export default function SpringHeader() {
    const navigate=useNavigate(),dispatch=useDispatch();
    const permissions=useSelector(s=>s.config)||[];
    const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar';
    const [error,setError]=useState('');
    async function language(e) {
        try {await api.post('change-language',{language:e.target.value});localStorage.setItem(Tokens.UPDATED_LANGUAGE,e.target.value);window.location.reload();}
        catch(e){setError(e.response.data.message);}
    }
    return <div className="d-flex align-items-center gap-3"><Link to="/app/dashboard">MPOS</Link>
        {permissions.includes('manage_purchase')&&<Link to="/app/purchases">{ar?'المشتريات':'Purchases'}</Link>}
        {permissions.includes('manage_inventory')&&<Link to="/app/inventory">{ar?'المخزون':'Inventory'}</Link>}
        <select aria-label="Language" className="form-select" value={ar?'ar':'en'} onChange={language}><option value="en">English</option><option value="ar">العربية</option></select>
        <button className="btn btn-outline-primary text-nowrap" onClick={()=>dispatch(logoutAction({},navigate))}>{ar?'تسجيل الخروج':'Sign out'}</button>
        {error && <span role="alert">{error}</span>}</div>;
}
