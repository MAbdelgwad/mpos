const {chromium}=require('playwright');
const http=require('http');
const fs=require('fs');
const path=require('path');
const assert=require('node:assert/strict');
(async()=>{
 const backend=Number(process.argv[2]);
 const root=path.resolve(__dirname,'../dist');
 const server=http.createServer((req,res)=>{
  if(req.url.startsWith('/api/')){
   const upstream=http.request({hostname:'127.0.0.1',port:backend,path:req.url,method:req.method,headers:req.headers},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res);});
   upstream.on('error',()=>{res.writeHead(502);res.end();});req.pipe(upstream);return;
  }
  const file=path.resolve(root,'.'+decodeURIComponent(req.url.split('?')[0]));
  if(!file.startsWith(root+path.sep) && file!==root){res.writeHead(403);res.end();return;}
  const target=fs.existsSync(file) && fs.statSync(file).isFile()?file:path.join(root,'index.html');
  const mime={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.woff2':'font/woff2'}[path.extname(target)]||'application/octet-stream';
  res.setHeader('Content-Type',mime);fs.createReadStream(target).pipe(res);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 let browser;
 try{
  browser=await chromium.launch({headless:true,executablePath:process.env.MPOS_CHROMIUM,args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  page.setDefaultTimeout(20000);
  const errors=[],failures=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{if(r.url().includes('/api/') && r.status()>=400)failures.push(r.status()+' '+r.url());});
  const origin='http://127.0.0.1:'+server.address().port;
  await page.goto(origin+'/#/login');
  await page.locator('input[name=email]').fill('admin@example.com');
  await page.locator('input[name=password]').fill('test-only-long-password');
  await page.getByRole('button',{name:'Login',exact:true}).click();
  await page.getByRole('heading',{name:'Master data',exact:true}).waitFor();
  await page.goto(origin+'/#/app/brands');
  await page.getByRole('button',{name:'Create Brand',exact:true}).click();
  await page.locator('.modal input[name=name]').fill('Browser Brand');
  await page.locator('.modal').getByRole('button',{name:'Save',exact:true}).click();
  await page.getByText('Browser Brand',{exact:true}).first().waitFor();
  // All migrated screens must load without missing API endpoints or runtime errors.
  for(const route of ['product-categories','base-units','units','warehouse','suppliers','customers','products','users','roles','settings','purchases','inventory']){
   await page.goto(origin+'/#/app/'+route);
   await page.waitForLoadState('networkidle');
   assert(!page.url().endsWith('/login'),'Unexpected logout at '+route);
  }
  await page.goto(origin+'/#/app/customers/create');
  await page.locator('input[name=name]').fill('Browser Customer');
  await page.locator('input[name=email]').fill('browser@example.com');
  await page.locator('input[name=phone]').fill('0501234567');
  await page.locator('[name=country]').fill('Saudi Arabia');
  await page.locator('[name=city]').fill('Riyadh');
  await page.locator('[name=address]').fill('Browser test address');
  await page.getByRole('button',{name:'Save',exact:true}).click();
  await page.getByText('Browser Customer',{exact:true}).first().waitFor();
  await page.getByRole('combobox',{name:'Language',exact:true}).selectOption('ar');
  await page.waitForLoadState('networkidle');
  await page.waitForFunction(()=>document.documentElement.dir==='rtl');
  assert.equal(errors.length,0,'Browser errors: '+errors.join('\n'));
  assert.equal(failures.length,0,'API failures: '+failures.join('\n'));
  console.log('Browser smoke passed: login, brand/customer creation, 12 screens, Arabic RTL.');
 }catch(e){console.error(e);process.exitCode=1;}
 finally{if(browser)await browser.close();server.close();}
})();
