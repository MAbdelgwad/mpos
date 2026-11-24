import {useDispatch} from 'react-redux';
import {fetchFrontSetting} from '../../store/action/frontSettingAction';
import MailSettings from './MailSettings';
import InvoiceSettings from '../invoices/InvoiceSettings';
import React,{useState,useEffect} from 'react';
import MasterLayout from '../../components/MasterLayout';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
export default function SpringSettings(){
    const dispatch=useDispatch();
    const [values,setValues]=useState(null),[message,setMessage]=useState('');
    const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar';
    useEffect(()=>{api.get('ui-settings').then(r=>setValues(r.data.data)).catch(e=>setMessage(e.response?.data?.message || (ar?'تعذر إتمام الطلب':'Request failed')));},[]);
    async function save(e){e.preventDefault();try{const response=await api.put('ui-settings',values);setValues(response.data.data);dispatch(fetchFrontSetting());setMessage(ar?'تم الحفظ':'Saved');}catch(e){setMessage(e.response?.data?.message || (ar?'تعذر إتمام الطلب':'Request failed'));}}
    const labels={company_name:ar?'اسم الشركة':'Company name',currency_symbol:ar?'رمز العملة':'Currency symbol',footer:ar?'تذييل الصفحة':'Footer'};
    const change=e=>setValues({...values,[e.target.name]:e.target.value});
    return <MasterLayout><h1>{ar?'إعدادات الشركة':'Company settings'}</h1><p role="status">{message}</p>{values && <form onSubmit={save} className="card p-5">
        {Object.entries(labels).map(([key,label])=><label className="mb-4" key={key}>{label}<input className="form-control" name={key} disabled={key==='currency_symbol'&&!!values.currency_code} value={values[key]} onChange={change} maxLength={500} required={key==='company_name'}/></label>)}
        <label>{ar?'اللغة الافتراضية':'Default language'}<select className="form-select mb-4" name="default_language" value={values.default_language} onChange={change}><option value="en">English</option><option value="ar">العربية</option></select></label>
        <label>{ar?'صيغة التاريخ':'Date format'}<select className="form-select mb-4" name="date_format" value={values.date_format} onChange={change}><option value="d-m-y">DD-MM-YYYY</option><option value="m-d-y">MM-DD-YYYY</option><option value="y-m-d">YYYY-MM-DD</option></select></label>
        <label className="d-block mb-4">{ar?'العملة الافتراضية للفواتير الجديدة':'Default currency for new invoices'}<select className="form-select" name="currency_code" value={values.currency_code||''} onChange={change}><option value="">{ar?'مخصصة / بدون رمز دولي':'Custom / no ISO code'}</option><option value="USD">USD — الدولار الأمريكي</option><option value="SAR">SAR — الريال السعودي</option><option value="EGP">EGP — الجنيه المصري</option></select><small>{ar?'لا يتم تحويل الأسعار أو أرصدة المخزون. تحتفظ الفواتير القديمة بعملتها.':'No price conversion. Existing invoices retain their currency.'}</small></label><InvoiceSettings values={values} change={change} ar={ar}/><button className="btn btn-primary" type="submit">{ar?'حفظ':'Save'}</button></form>}<MailSettings ar={ar}/></MasterLayout>;
}
