import React,{useState,useEffect} from 'react';
import MasterLayout from '../../components/MasterLayout';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
export default function SpringSettings(){
    const [values,setValues]=useState(null),[message,setMessage]=useState('');
    const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar';
    useEffect(()=>{api.get('ui-settings').then(r=>setValues(r.data.data)).catch(e=>setMessage(e.response.data.message));},[]);
    async function save(e){e.preventDefault();try{await api.put('ui-settings',values);setMessage(ar?'تم الحفظ':'Saved');}catch(e){setMessage(e.response.data.message);}}
    const labels={company_name:ar?'اسم الشركة':'Company name',currency_symbol:ar?'رمز العملة':'Currency symbol',footer:ar?'تذييل الصفحة':'Footer'};
    const change=e=>setValues({...values,[e.target.name]:e.target.value});
    return <MasterLayout><h1>{ar?'إعدادات الشركة':'Company settings'}</h1><p role="status">{message}</p>{values && <form onSubmit={save} className="card p-5">
        {Object.entries(labels).map(([key,label])=><label className="mb-4" key={key}>{label}<input className="form-control" name={key} value={values[key]} onChange={change} maxLength={500} required={key==='company_name'}/></label>)}
        <label>{ar?'اللغة الافتراضية':'Default language'}<select className="form-select mb-4" name="default_language" value={values.default_language} onChange={change}><option value="en">English</option><option value="ar">العربية</option></select></label>
        <label>{ar?'صيغة التاريخ':'Date format'}<select className="form-select mb-4" name="date_format" value={values.date_format} onChange={change}><option value="d-m-y">DD-MM-YYYY</option><option value="m-d-y">MM-DD-YYYY</option><option value="y-m-d">YYYY-MM-DD</option></select></label>
        <button className="btn btn-primary" type="submit">{ar?'حفظ':'Save'}</button></form>}</MasterLayout>;
}
