const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const babel=require('@babel/core');
const {createStore,combineReducers}=require('redux');
const constants={SET_TOTAL_RECORD:'TOTAL',UPDATE_TOTAL_RECORD_AFTER_ADD:'ADD',UPDATE_TOTAL_RECORD_AFTER_DELETE:'REMOVE'};
function compile(file,mocks){
 const code=babel.transformSync(fs.readFileSync(path.join(__dirname,file),'utf8'),{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code;
 const exports={};vm.runInNewContext(code,{exports,require:name=>{if(name in mocks)return mocks[name];throw Error('Unexpected import '+name);},_:require('lodash')});return exports;
}
function fixture(response,error){
 const constantModule={constants,apiBaseURL:{PRODUCTS_CATEGORIES:'product-categories'},productCategoriesActionType:{FETCH_PRODUCTS_CATEGORIES:'CATEGORIES'},toastType:{ERROR:'error'}};
 const totalActions=compile('../src/store/action/totalRecordAction.js',{'../../constants':constantModule});
 const reducer=compile('../src/store/reducers/totalRecordReduce.js',{'../../constants':constantModule}).default;
 const store=createStore(combineReducers({total:reducer}));const actions=[];
 const action=compile('../src/store/action/productCategoryAction.js',{
 '../../config/apiConfig':{get:()=>error?Promise.reject(error):Promise.resolve(response)},'../../constants':constantModule,
 './toastAction':{addToast:payload=>({type:'TOAST',payload})},'./totalRecordAction':totalActions,
 '../../shared/requestParam':()=>'', './loadingAction':{setLoading:payload=>({type:'LOADING',payload})},
 '../../shared/sharedMethod':{getFormattedMessage:x=>x}
 }).fetchProductCategories;
 return {store,actions,run:()=>action() (a=>{actions.push(a);return store.dispatch(a);})};
}
test('empty successful category list keeps Redux total zero and finishes loading',async()=>{
 const f=fixture({data:{data:[],meta:{total:0}}});await f.run();
 assert.equal(f.store.getState().total,0);assert(!f.actions.some(a=>a.type==='TOAST'));
 assert.equal(f.actions.at(-1).type,'LOADING');assert.equal(f.actions.at(-1).payload,false);
});
test('nonempty successful category list preserves total',async()=>{
 const f=fixture({data:{data:[{id:1}],meta:{total:12}}});await f.run();assert.equal(f.store.getState().total,12);
});
test('HTTP and JavaScript failures show original message without a second exception',async()=>{
 for(const error of [{response:{data:{message:'Forbidden'}}},new Error('Processing failed')]){
  const f=fixture(null,error);await f.run();assert.equal(f.actions.find(a=>a.type==='TOAST').payload.text,error.response?.data.message||error.message);
  assert.equal(f.actions.at(-1).payload,false);
 }
});
