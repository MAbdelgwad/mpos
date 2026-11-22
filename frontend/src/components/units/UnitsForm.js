import React,{useState,useEffect} from 'react';
import {Modal} from 'react-bootstrap-v5';
import {useDispatch} from 'react-redux';
import api from '../../config/apiConfig';
import {fetchUnits} from '../../store/action/unitsAction';
import {fetchUnitChoices} from '../../store/action/unitChoicesAction';
import {Tokens} from '../../constants';
export default function UnitsForm({handleClose,hide,show,title,singleUnit}) {
 const dispatch=useDispatch(),ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar',t=(a,e)=>ar?a:e;
 const [value,setValue]=useState({name:'',short_name:''}),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{if(show){setValue({name:singleUnit?.name||'',short_name:singleUnit?.short_name||''});setError('');}},[show,singleUnit]);
 const close=()=>{if(!busy)(handleClose||hide)?.(false);};
 async function save(e){e.preventDefault();if(busy)return;setBusy(true);setError('');try{const data={name:value.name.trim(),short_name:value.short_name.trim()};if(!data.name||!data.short_name)throw new Error(t('أدخل الاسم والاختصار','Enter name and abbreviation'));if(singleUnit)await api.put('units/'+singleUnit.id,data);else await api.post('units',data);dispatch(fetchUnits());dispatch(fetchUnitChoices());(handleClose||hide)?.(false);}catch(e){setError(e.response?.data?.message||e.message);}finally{setBusy(false);}}
 return <Modal show={show} onHide={close}><Modal.Header closeButton><Modal.Title>{title}</Modal.Title></Modal.Header><form onSubmit={save}><Modal.Body>{error&&<div role="alert" className="alert alert-danger">{error}</div>}
 {[['name',t('اسم الوحدة','Unit name'),255],['short_name',t('الاختصار','Abbreviation'),50]].map(([key,label,max])=><label key={key} className="d-block mb-3">{label}<input required maxLength={max} className="form-control" value={value[key]} onChange={e=>setValue({...value,[key]:e.target.value})}/></label>)}
 </Modal.Body><Modal.Footer><button disabled={busy} type="submit" className="btn btn-primary">{t('حفظ','Save')}</button><button disabled={busy} type="button" className="btn btn-light" onClick={close}>{t('إغلاق','Close')}</button></Modal.Footer></form></Modal>;
}
