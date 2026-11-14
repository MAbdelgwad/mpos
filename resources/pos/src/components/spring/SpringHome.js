import React, {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import MasterLayout from '../MasterLayout';
import api from '../../config/apiConfig';
import {Tokens} from '../../constants';
const labels = {
    warehouses: ['المخازن', 'Warehouses', 'warehouse'],
    brands: ['العلامات التجارية', 'Brands', 'brands'],
    'product-categories': ['التصنيفات', 'Categories', 'product-categories'],
    'base-units': ['الوحدات الأساسية', 'Base units', 'base-units'],
    units: ['وحدات القياس', 'Units', 'units'],
    customers: ['العملاء', 'Customers', 'customers'],
    suppliers: ['الموردون', 'Suppliers', 'suppliers'],
    products: ['المنتجات', 'Products', 'products'],
};
export default function SpringHome() {
    const [data,setData]=useState(null), [error,setError]=useState('');
    const ar=localStorage.getItem(Tokens.UPDATED_LANGUAGE)==='ar';
    useEffect(()=>{api.get('catalog-summary').then(r=>setData(r.data.data)).catch(e=>setError(e.response.data.message));},[]);
    return <MasterLayout><h1>{ar?'البيانات الأساسية':'Master data'}</h1>
        <p>{ar?'إدارة المنتجات والعملاء والموردين والمخازن. المشتريات والمخزون متاحان من الشريط العلوي. المبيعات غير متاحة بعد.':'Manage products, contacts and warehouses. Purchases and inventory are available in the top bar. Sales are not available yet.'}</p>
        {error && <div role="alert" className="alert alert-danger">{error}</div>}
        {!data && !error && <p>{ar?'جاري التحميل…':'Loading…'}</p>}
        <div className="row">{data && Object.entries(data).map(([key,count])=><div className="col-md-4 mb-4" key={key}><Link className="card p-5" to={'/app/'+labels[key][2]}><h3>{labels[key][ar?0:1]}</h3><strong>{count}</strong></Link></div>)}</div>
        {data && Object.keys(data).length===0 && <p>{ar?'لا توجد صلاحيات للبيانات الأساسية. تواصل مع المسؤول.':'No master-data permissions assigned. Contact your administrator.'}</p>}
    </MasterLayout>;
}
