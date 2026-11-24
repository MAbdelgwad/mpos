const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const babel=require('@babel/core'),React=require('react'),server=require('react-dom/server');
function compile(file,mocks){const code=babel.transformSync(fs.readFileSync(path.join(__dirname,'../src/features/invoices',file),'utf8'),{babelrc:false,configFile:false,presets:[require.resolve('@babel/preset-react')],plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code;const exports={};vm.runInNewContext(code,{exports,require:n=>{if(n in mocks)return mocks[n];throw Error(n);}});return exports;}
const docModule=compile('InvoiceDocument.js',{react:React});
const printModule=compile('InvoicePrintButton.js',{react:React,'react-dom/server':server,'../../config/apiConfig':{},'../../constants':{},'./InvoiceDocument':docModule,'./InvoiceShareButton':()=>null});
const line={id:1,name:'أرز <script>alert(1)</script>',code:'CODE-X',quantity:'2.5000',product_price:'10.0000',product_cost:'7.0000',discount_amount:'1.0000',tax_amount:'3.6000',tax_value:15,tax_type:1,sub_total:'27.6000'};
function doc(type,status=1){return {attributes:{reference_code:'INV-<123>',date:'2026-11-22',status,customer_name:'عميل',supplier_name:'مورد',warehouse_name:'المخزن',grand_total:'29.6000',discount:0,tax_rate:0,tax_amount:0,shipping:2,paid_amount:'10.0000',due_amount:'19.6000',notes:'PRIVATE-NOTE',[type==='sales'?'sale_items':'purchase_items']:[line]}};}
test('sale and purchase use server values, correct price fields and escape text',()=>{
 for(const type of ['sales','purchases']){const html=printModule.invoiceHtml(doc(type),type,{company_name:'<img src=x onerror=alert(1)>',invoice_language:'ar',invoice_footer:'<script>bad()</script>'});assert.match(html,/dir="rtl"/);assert.match(html,/29\.6000/);assert.match(html,/2\.5000/);assert.match(html,type==='sales'?/10\.0000/:/7\.0000/);assert(!html.includes('<script>'));assert.match(html,/&lt;script&gt;/);assert.match(html,type==='sales'?/عميل/:/مورد/);}
});
test('paper sizes, templates and field visibility apply to printable HTML',()=>{
 for(const paper of ['A4','A5','80mm'])for(const template of ['classic','modern','compact']){const html=printModule.invoiceHtml(doc('sales'),'sales',{invoice_paper:paper,invoice_template:template,invoice_language:'en',invoice_show_code:'false',invoice_show_notes:'false',invoice_show_payments:'false',invoice_show_signature:'true'});assert.match(html,/lang="en"/);assert(!html.includes('CODE-X'));assert(!html.includes('PRIVATE-NOTE'));assert(!html.includes('19.6000'));assert.match(html,/Recipient signature/);assert(html.includes(paper==='80mm'?'receipt-line':'<table>'));}
});
test('draft, ordered and cancelled status are always visible',()=>{
 for(const [status,label] of [[2,'Pending — not posted'],[3,'Order — not posted'],[4,'Cancelled']])assert(printModule.invoiceHtml(doc('sales',status),'sales',{invoice_language:'en'}).includes(label));
});
test('unsafe layout settings cannot inject CSS or markup',()=>{
 const css=docModule.invoiceCss({invoice_paper:'A4;}body{display:none}',invoice_color:'</style><script>bad()</script>',invoice_font_size:'1;display:none'});assert(!css.includes('<script>'));assert(!css.includes('display:none'));assert(css.includes('#243b53'));assert(css.includes('font-size:12px'));
});

test('invoice currency snapshot wins over changed default',()=>{const d=doc('sales');d.attributes.currency_symbol='USD';const html=printModule.invoiceHtml(d,'sales',{currency_symbol:'SAR'});assert(html.includes('USD'));assert(!html.includes('SAR'));});

test('shipment references and container quantities print safely',()=>{const d=doc('sales');d.attributes.import_containers=[{booking_number:'BOOK-<x>',container_number:'ABCD1234567',pieces:3}];const html=printModule.invoiceHtml(d,'sales',{});assert(html.includes('BOOK-&lt;x&gt;'));assert(html.includes('ABCD1234567'));assert(!html.includes('<x>'));});
