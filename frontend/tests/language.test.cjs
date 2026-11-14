const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const babel=require('@babel/core');
const {JSDOM}=require('jsdom');
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://localhost/',pretendToBeVisual:true});
global.MessageChannel=undefined;
global.window=dom.window;global.document=dom.window.document;global.navigator=dom.window.navigator;
const React=require('react');
const ReactDOM=require('react-dom');
const {act,Simulate}=require('react-dom/test-utils');
const {languageSelection}=require('../src/locales/selection');
const locales={en:require('../src/locales/en.json'),ar:require('../src/locales/ar.json')};
function header(api,reload){
 const source=fs.readFileSync(path.join(__dirname,'../src/app/SpringHeader.js'),'utf8');
 const code=babel.transformSync(source,{babelrc:false,configFile:false,presets:[require.resolve('@babel/preset-react')],plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code;
 const mocks={'react':React,'react-router-dom':{Link:({children})=>React.createElement('span',null,children),useNavigate:()=>()=>{}},'react-redux':{useDispatch:()=>()=>{},useSelector:()=>[]},'../store/action/authAction':{logoutAction:()=>{}},'../constants':{Tokens:{UPDATED_LANGUAGE:'updated_language',LANGUAGE:'language'}},'../config/apiConfig':api,'../shared/diagnostics/diagnostics':{downloadDiagnostics:()=>{}}};
 const exports={};vm.runInNewContext(code,{exports,require:name=>mocks[name],localStorage:window.localStorage,window:{location:{reload}}});return exports.default;
}
test('controlled language selection persists captured value after delayed response in both directions',async()=>{
 const root=document.getElementById('root');
 for(const [before,after] of [['en','ar'],['ar','en']]) {
  window.localStorage.setItem('updated_language',before);let resolve,requested,reloaded=0;
  const Header=header({post:(url,body)=>{assert.equal(url,'change-language');requested=body.language;return new Promise(r=>{resolve=r;});}},()=>{reloaded++;});
  act(()=>{ReactDOM.render(React.createElement(Header),root);});
  const select=root.querySelector('select');
  act(()=>{select.value=after;Simulate.change(select);});
  assert.equal(requested,after);assert.equal(select.value,before);assert.equal(select.disabled,true);
  await act(async()=>{resolve({data:{success:true}});});
  assert.equal(window.localStorage.getItem('updated_language'),after);assert.equal(window.localStorage.getItem('language'),after);assert.equal(reloaded,1);
  act(()=>{ReactDOM.unmountComponentAtNode(root);});
 }
});
test('failed language update retains previous language and re-enables selector',async()=>{
 window.localStorage.setItem('updated_language','en');let reject,reloaded=0;
 const Header=header({post:()=>new Promise((_,r)=>{reject=r;})},()=>{reloaded++;});const root=document.getElementById('root');
 act(()=>{ReactDOM.render(React.createElement(Header),root);});const select=root.querySelector('select');
 act(()=>{select.value='ar';Simulate.change(select);});
 await act(async()=>{reject(new Error('Network unavailable'));});
 assert.equal(window.localStorage.getItem('updated_language'),'en');assert.equal(reloaded,0);assert.equal(select.disabled,false);assert(root.textContent.includes('Network unavailable'));
 act(()=>{ReactDOM.unmountComponentAtNode(root);});
});
test('Arabic and English use matching dictionaries and locales with fallback',()=>{
 const arabic=languageSelection('ar','en',locales);assert.equal(arabic.language,'ar');assert.equal(arabic.direction,'rtl');assert.equal(arabic.messages['product.categories.title'],locales.ar['product.categories.title']);
 const english=languageSelection('en','ar',locales);assert.equal(english.language,'en');assert.equal(english.direction,'ltr');assert.equal(english.messages['product.categories.title'],locales.en['product.categories.title']);
 assert.equal(languageSelection('undefined','en',locales).language,'en');
 assert.equal(languageSelection('ar','en',{en:{fallback:'Default'},ar:{}}).messages.fallback,'Default');
});

test.after(()=>dom.window.close());
