import React from 'react';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
export const isArabic=()=>localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar';
export const text=(a,e)=>isArabic()?a:e;
export const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
export const errorText=e=>e.response?.data?.message||text('تعذر إتمام الطلب','Request failed');
export const kinds={inspection_request:['طلب الفحص','Inspection request'],food_safety:['سلامة الغذاء','Food safety'],origin_certificate:['شهادة المنشأ','Certificate of origin'],booking_confirmation:['تأكيد الحجز','Booking confirmation'],other:['مستند آخر','Other document']};
export const expenses={sea_freight:['الشحن البحري','Sea freight'],land_transport:['النقل البري','Land transport'],clearance:['التخليص','Clearance'],goods:['شراء البضاعة','Goods purchase'],other:['مصروفات أخرى','Other expenses']};
export const status=s=>({draft:text('مسودة / قيد الشحن','Draft / in transit'),received:text('مستلمة بالمخزن','Received'),cancelled:text('ملغاة','Cancelled')})[s]||s;
export function Input({label,value,onChange,type='text',required=true,...props}){return <label className="d-block mb-3">{label}<input className="form-control" type={type} required={required} value={value??''} onChange={e=>onChange(e.target.value)} {...props}/></label>;}
export function Select({label,value,onChange,options,required=true}){return <label className="d-block mb-3">{label}<select className="form-select" required={required} value={value??''} onChange={e=>onChange(e.target.value)}><option value="">—</option>{options.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></label>;}
export async function download(shipment,file,name){const r=await api.get(`import-shipments/${shipment}/files/${file}`,{responseType:'blob'});const url=URL.createObjectURL(r.data),a=document.createElement('a');a.href=url;a.download=name||'attachment';a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);}
