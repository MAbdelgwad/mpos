const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const babel=require('@babel/core');
const generate=require('@babel/generator').default;
const traverse=require('@babel/traverse').default;
const source=fs.readFileSync(__dirname+'/../src/components/product/ProductForm.js','utf8');
const ast=babel.parseSync(source,{babelrc:false,configFile:false,presets:[require.resolve('@babel/preset-react')]});
function formFunction(name,context){let node;traverse(ast,{VariableDeclarator(p){if(p.node.id.name===name)node=p.node.init;}});return vm.runInNewContext('('+generate(node).code+')',context);}
test('Spring product saves without hidden warehouse, supplier or opening stock fields',()=>{
 const unit={value:7,label:'جوال'}, product={name:'بصل',code:'ONION',product_category_id:{value:1},brand_id:{value:2},barcode_symbol:{value:1},product_cost:'0',product_price:'10',product_unit:unit,sale_unit:unit,purchase_unit:unit,order_tax:0,notes:'',isEdit:false,tax_type:{value:1}};
 let errors;
 const context={window:{MPOS_SPRING:true},productValue:product,isClearDropdown:true,isDropdown:true,getFormattedMessage:x=>x,setErrors:e=>{errors=e;},FormData,multipleFiles:[]};
 assert.equal(formFunction('handleValidation',context)(),true);
 assert.equal(Object.keys(errors).length,0);
 const data=formFunction('prepareFormData',context)(product);
 assert.equal(data.get('name'),'بصل');assert.equal(data.get('product_unit'),'7');
 assert.equal(data.has('purchase_supplier_id'),false);assert.equal(data.has('purchase_quantity'),false);
 product.name='';assert.equal(formFunction('handleValidation',context)(),false);assert(errors.name);
});
const code=babel.transformSync(fs.readFileSync(__dirname+'/../src/features/imports/navigation.js','utf8'),{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code;
const exportsObject={};vm.runInNewContext(code,{exports:exportsObject,URLSearchParams,encodeURIComponent});
test('sidebar opens requested shipment section and retains only selected shipment',()=>{
 for(const section of ['documents','expenses','sales']){
  const target='/app/import-shipments/'+section;
  assert.equal(exportsObject.shipmentMenuTarget(target,{pathname:'/app/import-shipments',search:'?shipment=42&other=x'}),target+'?shipment=42');
  assert.equal(exportsObject.shipmentMenuTarget(target,{pathname:'/app/products',search:'?shipment=42'}),target);
  assert.equal(exportsObject.shipmentMenuTarget(target,{pathname:'/app/import-shipments',search:''}),target);
 }
 assert.equal(exportsObject.shipmentMenuTarget('/app/products',{pathname:'/app/import-shipments',search:'?shipment=42'}),'/app/products');
});
