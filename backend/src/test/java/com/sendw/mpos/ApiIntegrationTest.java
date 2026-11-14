package com.sendw.mpos;
import com.sendw.mpos.shared.infrastructure.Idempotency;
import com.sendw.mpos.identity.application.IdentityService;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    static final EmbeddedPostgres postgres;
    static {
        try { postgres=EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").start(); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres","postgres"));
        p.add("spring.datasource.username", () -> "postgres");
        p.add("spring.datasource.password", () -> "postgres");
        p.add("mpos.bootstrap-email", () -> "admin@example.com");
        p.add("mpos.bootstrap-password", () -> "test-only-long-password");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    final HttpClient http=HttpClient.newHttpClient();
    static final String WAREHOUSE="""
        {"name":"المخزن الرئيسي","phone":"0501234567","country":"Saudi Arabia","city":"Riyadh","email":"","zip_code":"00123"}
        """;
    @BeforeEach void clear() { db.update("DELETE FROM access_tokens"); db.execute("TRUNCATE sale_items,sales,stock_movements,stock_operations,stock_balances,purchase_items,purchases,request_keys,warehouses,products,units,base_units,brands,product_categories,customers,suppliers,media CASCADE"); db.update("DELETE FROM app_users WHERE email<>'admin@example.com'"); db.update("DELETE FROM roles WHERE protected_role=false"); }
    @AfterAll static void stopDatabase() throws Exception { postgres.close(); }

    HttpResponse<String> request(String method,String path,String token,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json");
        if(token!=null) b.header("Authorization","Bearer "+token);
        return http.send(b.method(method, body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String login() throws Exception {
        var r=request("POST","/api/login",null,"{\"email\":\"ADMIN@EXAMPLE.COM\",\"password\":\"test-only-long-password\"}");
        assertEquals(200,r.statusCode(),r.body());
        assertFalse(r.body().contains("password_hash"));
        return JsonPath.read(r.body(),"$.data.token");
    }
    @Test void loginRevocationExpiryAndAuthentication() throws Exception {
        assertEquals(200,request("GET","/api/health",null,null).statusCode());
        var denied=request("GET","/api/warehouses",null,null);
        assertEquals(401,denied.statusCode());
        assertDoesNotThrow(()->java.util.UUID.fromString(denied.headers().firstValue("X-Request-ID").orElseThrow()));
        assertEquals(401,request("POST","/api/login",null,"{\"email\":\"admin@example.com\",\"password\":\"wrong\"}").statusCode());
        assertEquals(422,request("POST","/api/login",null,"{}").statusCode());
        String token=login();
        assertEquals(1,db.queryForObject("SELECT count(*) FROM access_tokens WHERE token_hash=?",Integer.class,IdentityService.digest(token)));
        assertEquals(200,request("GET","/api/warehouses",token,null).statusCode());
        assertEquals(200,request("POST","/api/logout",token,null).statusCode());
        assertEquals(401,request("GET","/api/warehouses",token,null).statusCode());
        token=login();
        db.update("UPDATE access_tokens SET expires_at=now()-interval '1 minute'");
        assertEquals(401,request("GET","/api/warehouses",token,null).statusCode());
    }
    @Test void warehouseCrudAndLegacyResponseShape() throws Exception {
        String token=login();
        var created=request("POST","/api/warehouses",token,WAREHOUSE);
        assertEquals(201,created.statusCode(),created.body());
        Number id=JsonPath.read(created.body(),"$.data.id");
        assertEquals("warehouses",JsonPath.read(created.body(),"$.data.type"));
        assertEquals("المخزن الرئيسي",JsonPath.read(created.body(),"$.data.attributes.name"));
        assertEquals("00123",JsonPath.read(created.body(),"$.data.attributes.zip_code"));
        assertEquals(409,request("POST","/api/warehouses",token,WAREHOUSE).statusCode());
        var listed=request("GET","/api/warehouses?page%5Bsize%5D=1&page%5Bnumber%5D=1",token,null);
        assertEquals(1,((Number)JsonPath.read(listed.body(),"$.meta.total")).intValue());
        assertEquals(200,request("POST","/api/warehouses/"+id,token,WAREHOUSE.replace("Riyadh","Jeddah")).statusCode());
        var updated=request("GET","/api/warehouses/"+id,token,null);
        assertEquals("Jeddah",JsonPath.read(updated.body(),"$.data.attributes.city"));
        assertEquals(200,request("DELETE","/api/warehouses/"+id,token,null).statusCode());
        assertEquals(404,request("GET","/api/warehouses/"+id,token,null).statusCode());
        assertEquals(404,request("PUT","/api/warehouses/"+id,token,WAREHOUSE).statusCode());
    }
    @Test void validationLiteralSearchAndSortAllowlist() throws Exception {
        String token=login();
        assertEquals(422,request("POST","/api/warehouses",token,"{}").statusCode());
        assertEquals(400,request("POST","/api/warehouses",token,"{").statusCode());
        assertEquals(400,request("GET","/api/warehouses?sort=invalid",token,null).statusCode());
        assertEquals(400,request("GET","/api/warehouses?page%5Bsize%5D=-1",token,null).statusCode());
        request("POST","/api/warehouses",token,WAREHOUSE);
        request("POST","/api/warehouses",token,WAREHOUSE.replace("المخزن الرئيسي","100% Store"));
        var filtered=request("GET","/api/warehouses?filter%5Bsearch%5D=%25",token,null);
        assertEquals(1,((Number)JsonPath.read(filtered.body(),"$.meta.total")).intValue(),filtered.body());
        var all=request("GET","/api/warehouses?page%5Bsize%5D=0",token,null);
        assertEquals(2,((Number)JsonPath.read(all.body(),"$.meta.total")).intValue());
    }
    @Test void viewerCannotManageWarehouses() throws Exception {
        String token=login();
        try {
            db.update("UPDATE app_users SET role='VIEWER' WHERE email='admin@example.com'");
            assertEquals(403,request("GET","/api/warehouses",token,null).statusCode());
            assertEquals(403,request("POST","/api/warehouses",token,WAREHOUSE).statusCode());
        } finally { db.update("UPDATE app_users SET role='ADMIN' WHERE email='admin@example.com'"); }
    }
    long create(String kind,String token,String json) throws Exception {
        var r=request("POST","/api/"+kind,token,json);assertEquals(201,r.statusCode(),r.body());
        return ((Number)JsonPath.read(r.body(),"$.data.id")).longValue();
    }
    String product(long category,long brand,long base,long unit,String code) {
        return "{\"name\":\"منتج تجريبي\",\"code\":\""+code+"\",\"product_category_id\":"+category+",\"brand_id\":"+brand+",\"product_cost\":12.3456,\"product_price\":20.50,\"product_unit\":"+base+",\"sale_unit\":"+unit+",\"purchase_unit\":"+unit+",\"barcode_symbol\":1,\"tax_type\":1,\"order_tax\":15}";
    }
    @Test void catalogRelationsPrecisionAndDeletionProtection() throws Exception {
        String t=login();long brand=create("brands",t,"{\"name\":\"Brand\"}"),category=create("product-categories",t,"{\"name\":\"Category\"}"),base=create("base-units",t,"{\"name\":\"Piece\"}");
        long unit=create("units",t,"{\"name\":\"Each\",\"short_name\":\"ea\",\"base_unit\":"+base+"}");
        String body=product(category,brand,base,unit,"SKU001");long id=create("products",t,body);
        assertEquals(new java.math.BigDecimal("12.3456"),db.queryForObject("SELECT product_cost FROM products WHERE id=?",java.math.BigDecimal.class,id));
        var shown=request("GET","/api/products/"+id,t,null);assertEquals("Brand",JsonPath.read(shown.body(),"$.data.attributes.brand_name"));
        assertTrue(((String)JsonPath.read(shown.body(),"$.data.attributes.barcode_image_url")).startsWith("data:image/png;base64,"));
        assertEquals(409,request("DELETE","/api/brands/"+brand,t,null).statusCode());
        assertEquals(409,request("POST","/api/products",t,body).statusCode());
        assertEquals(422,request("POST","/api/products",t,body.replace("SKU001","BAD").replace("12.3456","-1")).statusCode());
        assertEquals(422,request("POST","/api/products",t,body.replace("SKU001","BAD").replace("12.3456","1.12345")).statusCode());
        long other=create("base-units",t,"{\"name\":\"Weight\"}");
        assertEquals(422,request("POST","/api/products",t,product(category,brand,other,unit,"BAD")).statusCode());
        assertEquals(422,request("PUT","/api/units/"+unit,t,"{\"name\":\"Each\",\"short_name\":\"ea\",\"base_unit\":"+other+"}").statusCode());
        assertEquals(200,request("DELETE","/api/products/"+id,t,null).statusCode());
        assertEquals(200,request("DELETE","/api/brands/"+brand,t,null).statusCode());
    }
    @Test void contactsValidationUpdatesAndFiltering() throws Exception {
        String t=login();String contact="{\"name\":\"محمود\",\"email\":\"USER@EXAMPLE.COM\",\"phone\":\"0501234567\",\"country\":\"SA\",\"city\":\"Riyadh\",\"address\":\"Street\"}";
        long customer=create("customers",t,contact);create("suppliers",t,contact);
        assertEquals(409,request("POST","/api/customers",t,contact.replace("USER@EXAMPLE.COM","user@example.com")).statusCode());
        assertEquals(422,request("POST","/api/suppliers",t,contact.replace("USER@EXAMPLE.COM","bad")).statusCode());
        assertEquals(200,request("PUT","/api/customers/"+customer,t,contact.replace("Riyadh","Jeddah")).statusCode());
        var listed=request("GET","/api/customers?page%5Bsize%5D=0&sort=name",t,null);assertEquals(1,((Number)JsonPath.read(listed.body(),"$.meta.total")).intValue());
        assertEquals(422,request("GET","/api/customers?sort=password_hash",t,null).statusCode());
    }
    @Test void rolePermissionsApplyImmediatelyAndUsersCanBeDisabled() throws Exception {
        String admin=login();long permission=db.queryForObject("SELECT id FROM permissions WHERE name='manage_brands'",Long.class);
        long role=create("roles",admin,"{\"name\":\"Catalog clerk\",\"permissions\":["+permission+"]}");
        String user="{\"first_name\":\"Clerk\",\"last_name\":\"Test\",\"email\":\"clerk@example.com\",\"phone\":\"0501234567\",\"role_id\":"+role+",\"password\":\"clerk-test-password\",\"confirm_password\":\"clerk-test-password\"}";
        long uid=create("users",admin,user);
        var response=request("POST","/api/login",null,"{\"email\":\"clerk@example.com\",\"password\":\"clerk-test-password\"}");assertEquals(200,response.statusCode(),response.body());
        String token=JsonPath.read(response.body(),"$.data.token");
        assertEquals(200,request("GET","/api/brands",token,null).statusCode());
        assertEquals(403,request("GET","/api/products",token,null).statusCode());
        assertEquals(403,request("POST","/api/users",token,user).statusCode());
        assertEquals(200,request("PUT","/api/roles/"+role,admin,"{\"name\":\"Catalog clerk\",\"permissions\":[]}").statusCode());
        assertEquals(403,request("GET","/api/brands",token,null).statusCode());
        assertEquals(409,request("DELETE","/api/roles/"+role,admin,null).statusCode());
        assertEquals(200,request("PUT","/api/users/"+uid,admin,user.substring(0,user.length()-1)+",\"enabled\":false}").statusCode());
        assertEquals(401,request("GET","/api/config",token,null).statusCode());
        assertEquals(401,request("POST","/api/login",null,"{\"email\":\"clerk@example.com\",\"password\":\"clerk-test-password\"}").statusCode());
        long adminRole=db.queryForObject("SELECT id FROM roles WHERE name='ADMIN'",Long.class);
        assertEquals(422,request("DELETE","/api/roles/"+adminRole,admin,null).statusCode());
        assertFalse(request("GET","/api/users",admin,null).body().contains("password_hash"));
    }
    @Test void multipartLoginImagesAndRollback() throws Exception {
        String boundary="mpos-test-boundary";
        String loginBody="--"+boundary+"\r\nContent-Disposition: form-data; name=\"email\"\r\n\r\nadmin@example.com\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"password\"\r\n\r\ntest-only-long-password\r\n--"+boundary+"--\r\n";
        var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/login")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),response.body());String token=JsonPath.read(response.body(),"$.data.token");
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",png);
        var out=new java.io.ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nImage brand\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes());out.write(png.toByteArray());out.write(("\r\n--"+boundary+"--\r\n").getBytes());
        var upload=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/brands")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(201,upload.statusCode(),upload.body());String url=JsonPath.read(upload.body(),"$.data.attributes.image");
        var downloaded=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertArrayEquals(png.toByteArray(),downloaded.body());
        String invalid="--"+boundary+"\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nInvalid image brand\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"bad.png\"\r\nContent-Type: image/png\r\n\r\nnot-a-png\r\n--"+boundary+"--\r\n";
        var bad=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/brands")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(invalid)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(422,bad.statusCode(),bad.body());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM brands WHERE name='Invalid image brand'",Integer.class));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="mpos.browser",matches="true")
    void browserEndToEnd() throws Exception {
        var process=new ProcessBuilder("node","../frontend/tests/smoke.cjs",String.valueOf(port)).inheritIO().start();
        boolean ended=process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS);
        if(!ended)process.destroyForcibly();
        assertTrue(ended,"Browser smoke timed out");assertEquals(0,process.exitValue(),"Browser smoke failed");
    }
    @Test void companySettingsAreValidatedAndPersisted() throws Exception {
        String token=login();
        assertEquals(200,request("GET","/api/front-setting",null,null).statusCode());
        assertEquals(401,request("PUT","/api/ui-settings",null,"{\"company_name\":\"Unauthorized\"}").statusCode());
        assertEquals(200,request("PUT","/api/ui-settings",token,"{\"company_name\":\"شركة الاختبار\",\"date_format\":\"y-m-d\"}").statusCode());
        var front=request("GET","/api/front-setting",null,null);
        assertEquals("شركة الاختبار",JsonPath.read(front.body(),"$.data.value.company_name"));
        assertEquals("y-m-d",JsonPath.read(request("GET","/api/config",token,null).body(),"$.data.date_format"));
        assertEquals(422,request("PUT","/api/ui-settings",token,"{\"date_format\":\"invalid\"}").statusCode());
        assertEquals(422,request("PUT","/api/ui-settings",token,"{\"database_password\":\"bad\"}").statusCode());
    }
    @Test void urlEncodedUnitFormsMatchReact() throws Exception {
        String token=login();
        var base=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/base-units")).header("Authorization","Bearer "+token).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("name=Piece")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(201,base.statusCode(),base.body());Number id=JsonPath.read(base.body(),"$.data.id");
        var unit=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/units")).header("Authorization","Bearer "+token).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("name=Each&short_name=ea&base_unit="+id)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(201,unit.statusCode(),unit.body());
        assertEquals("Piece",JsonPath.read(unit.body(),"$.data.attributes.base_unit_name.name"));
    }
    @Test void passwordChangeDtoRevokesEverySessionAndPreservesPublicLoginContract() throws Exception {
        String admin=login();
        long viewer=db.queryForObject("SELECT id FROM roles WHERE name='VIEWER'",Long.class);
        create("users",admin,"""
            {"first_name":"Password test","email":"password-test@example.com","role_id":%d,
             "password":"old-long-password","confirm_password":"old-long-password"}
            """.formatted(viewer));
        String credentials="{\"email\":\"password-test@example.com\",\"password\":\"old-long-password\"}";
        var first=request("POST","/api/login",null,credentials);
        assertEquals(200,first.statusCode(),first.body());
        Map<String,Object> publicUser=JsonPath.read(first.body(),"$.data.user");
        assertEquals(java.util.Set.of("id","email","first_name","last_name","language","image_url"),publicUser.keySet());
        String token=JsonPath.read(first.body(),"$.data.token");
        String second=JsonPath.read(request("POST","/api/login",null,credentials).body(),"$.data.token");
        assertEquals(422,request("PATCH","/api/change-password",token,"{\"current_password\":\"old-long-password\",\"password\":\"new-long-password\",\"password_confirmation\":\"mismatch\"}").statusCode());
        assertEquals(200,request("GET","/api/config",second,null).statusCode());
        var changed=request("PATCH","/api/change-password",token,"{\"current_password\":\"old-long-password\",\"password\":\"new-long-password\",\"password_confirmation\":\"new-long-password\"}");
        assertEquals(200,changed.statusCode(),changed.body());
        assertEquals(401,request("GET","/api/config",token,null).statusCode());
        assertEquals(401,request("GET","/api/config",second,null).statusCode());
        assertEquals(401,request("POST","/api/login",null,credentials).statusCode());
        var fresh=request("POST","/api/login",null,credentials.replace("old-long-password","new-long-password"));
        assertEquals(200,fresh.statusCode(),fresh.body());
        String freshToken=JsonPath.read(fresh.body(),"$.data.token");
        assertEquals(422,request("POST","/api/change-language",freshToken,"{}").statusCode());
        assertEquals(200,request("POST","/api/change-language",freshToken,"{\"language\":\"ar\"}").statusCode());
        assertEquals("ar",JsonPath.read(request("POST","/api/login",null,credentials.replace("old-long-password","new-long-password")).body(),"$.data.user.language"));
    }

    @Test void invalidSettingsUpdateRollsBackEarlierValidFields() throws Exception {
        String token=login();
        String previous=db.queryForObject("SELECT value FROM ui_settings WHERE key='company_name'",String.class);
        var response=request("PUT","/api/ui-settings",token,"{\"company_name\":\"Must roll back\",\"default_language\":\"unsupported\"}");
        assertEquals(422,response.statusCode(),response.body());
        assertEquals(previous,db.queryForObject("SELECT value FROM ui_settings WHERE key='company_name'",String.class));
    }

    @Test void summaryRespectsPermissionsAcrossModuleRepositories() throws Exception {
        var fixture=stockFixture();
        var summary=request("GET","/api/catalog-summary",fixture.token(),null);
        assertEquals(200,summary.statusCode(),summary.body());
        assertEquals(2,((Number)JsonPath.read(summary.body(),"$.data.warehouses")).intValue());
        assertEquals(1,((Number)JsonPath.read(summary.body(),"$.data.products")).intValue());
        long permission=db.queryForObject("SELECT id FROM permissions WHERE name='manage_brands'",Long.class);
        long role=create("roles",fixture.token(),"{\"name\":\"Brand summary\",\"permissions\":["+permission+"]}");
        create("users",fixture.token(),"{\"first_name\":\"Summary\",\"email\":\"summary@example.com\",\"role_id\":"+role+",\"password\":\"summary-long-password\",\"confirm_password\":\"summary-long-password\"}");
        String token=JsonPath.read(request("POST","/api/login",null,"{\"email\":\"summary@example.com\",\"password\":\"summary-long-password\"}").body(),"$.data.token");
        Map<String,Object> restricted=JsonPath.read(request("GET","/api/catalog-summary",token,null).body(),"$.data");
        assertEquals(java.util.Set.of("brands"),restricted.keySet());
        assertEquals(1,((Number)restricted.get("brands")).intValue());
    }

    record StockFixture(String token,long warehouse,long target,long supplier,long product,long unit,long base,long brand,long category) {}
    StockFixture stockFixture() throws Exception {
        String token=login();long warehouse=create("warehouses",token,WAREHOUSE),target=create("warehouses",token,WAREHOUSE.replace("المخزن الرئيسي","Second warehouse"));
        long supplier=create("suppliers",token,"{\"name\":\"Supplier\",\"email\":\"supplier@example.com\",\"phone\":\"0501234567\",\"country\":\"SA\",\"city\":\"Riyadh\",\"address\":\"Street\"}");
        long brand=create("brands",token,"{\"name\":\"Stock brand\"}"),category=create("product-categories",token,"{\"name\":\"Stock category\"}"),base=create("base-units",token,"{\"name\":\"Stock base\"}");
        long unit=create("units",token,"{\"name\":\"Stock unit\",\"short_name\":\"ea\",\"base_unit\":"+base+"}");
        long product=create("products",token,product(category,brand,base,unit,"STOCK001"));return new StockFixture(token,warehouse,target,supplier,product,unit,base,brand,category);
    }
    String purchase(StockFixture f,int status,int quantity) {
        return "{\"supplier_id\":"+f.supplier()+",\"warehouse_id\":"+f.warehouse()+",\"date\":\"2026-11-14\",\"status\":"+status+",\"tax_rate\":15,\"discount\":0,\"shipping\":0,\"grand_total\":0,\"purchase_items\":[{\"product_id\":"+f.product()+",\"purchase_unit\":"+f.unit()+",\"product_cost\":115,\"quantity\":"+quantity+",\"tax_type\":2,\"tax_value\":15,\"discount_type\":2,\"discount_value\":0}]}";
    }
    HttpRequest keyed(String path,String token,String key,String body){return HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type","application/json").header("Authorization","Bearer "+token).header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body)).build();}
    HttpResponse<String> postKey(String path,String token,String key,String body)throws Exception{return http.send(keyed(path,token,key,body),HttpResponse.BodyHandlers.ofString());}
    String opening(StockFixture f,int qty){return "{\"product_id\":"+f.product()+",\"warehouse_id\":"+f.warehouse()+",\"quantity\":"+qty+",\"reason\":\"Opening stock\"}";}
    String adjustment(StockFixture f,int qty){return "{\"product_id\":"+f.product()+",\"warehouse_id\":"+f.warehouse()+",\"quantity_delta\":"+qty+",\"reason\":\"Physical count\"}";}
    void assertStock(long warehouse,long product,String expected){assertEquals(new java.math.BigDecimal(expected).setScale(4),db.queryForObject("SELECT quantity FROM stock_balances WHERE warehouse_id=? AND product_id=?",java.math.BigDecimal.class,warehouse,product));}
    @Test void purchaseDraftReceiveAndCancellationLedger() throws Exception {
        var f=stockFixture();var created=postKey("/api/purchases",f.token(),"purchase-draft-1",purchase(f,2,2));assertEquals(201,created.statusCode(),created.body());
        long id=((Number)JsonPath.read(created.body(),"$.data.id")).longValue();
        assertEquals(264.5,((Number)JsonPath.read(created.body(),"$.data.attributes.grand_total")).doubleValue(),0.00001);
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        var updated=request("PUT","/api/purchases/"+id,f.token(),purchase(f,2,3).replace("\"status\":2","\"status\":2,\"version\":0"));assertEquals(200,updated.statusCode(),updated.body());
        assertEquals(409,request("PUT","/api/purchases/"+id,f.token(),purchase(f,2,4).replace("\"status\":2","\"status\":2,\"version\":0")).statusCode());
        assertEquals(200,request("POST","/api/purchases/"+id+"/receive",f.token(),"{}").statusCode());assertStock(f.warehouse(),f.product(),"3");
        assertEquals(200,request("POST","/api/purchases/"+id+"/receive",f.token(),"{}").statusCode());assertEquals(1,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        assertEquals(409,request("PUT","/api/purchases/"+id,f.token(),purchase(f,2,4)).statusCode());
        assertEquals(200,request("POST","/api/purchases/"+id+"/cancel",f.token(),"{}").statusCode());assertStock(f.warehouse(),f.product(),"0");
        assertEquals(200,request("POST","/api/purchases/"+id+"/cancel",f.token(),"{}").statusCode());assertEquals(2,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        assertEquals(409,request("POST","/api/purchases/"+id+"/receive",f.token(),"{}").statusCode());
    }
    @Test void duplicateConcurrentReceiptsPostOnlyOnce() throws Exception {
        var f=stockFixture();String body=purchase(f,1,5);var req=keyed("/api/purchases",f.token(),"same-receipt-key",body);
        var a=http.sendAsync(req,HttpResponse.BodyHandlers.ofString());var b=http.sendAsync(req,HttpResponse.BodyHandlers.ofString());
        var first=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var second=b.get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(201,first.statusCode(),first.body());assertEquals(201,second.statusCode(),second.body());
        assertEquals((Number)JsonPath.read(first.body(),"$.data.id"),(Number)JsonPath.read(second.body(),"$.data.id"));
        assertStock(f.warehouse(),f.product(),"5");assertEquals(1,db.queryForObject("SELECT count(*) FROM purchases",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        assertEquals(409,postKey("/api/purchases",f.token(),"same-receipt-key",purchase(f,1,6)).statusCode());
    }
    @Test void openingTransfersAndOverdrawRollback() throws Exception {
        var f=stockFixture();assertEquals(200,postKey("/api/inventory/opening",f.token(),"opening-key-1",opening(f,10)).statusCode());
        assertEquals(200,postKey("/api/inventory/opening",f.token(),"opening-key-1",opening(f,10)).statusCode());
        assertEquals(409,postKey("/api/inventory/opening",f.token(),"opening-key-2",opening(f,10)).statusCode());
        String transfer="{\"product_id\":"+f.product()+",\"from_warehouse_id\":"+f.warehouse()+",\"to_warehouse_id\":"+f.target()+",\"quantity\":3,\"reason\":\"Restock target\"}";
        assertEquals(200,postKey("/api/inventory/transfers",f.token(),"transfer-key-1",transfer).statusCode());assertStock(f.warehouse(),f.product(),"7");assertStock(f.target(),f.product(),"3");
        assertEquals(409,postKey("/api/inventory/transfers",f.token(),"transfer-key-2",transfer.replace("\"quantity\":3","\"quantity\":8")).statusCode());
        assertStock(f.warehouse(),f.product(),"7");assertStock(f.target(),f.product(),"3");assertEquals(2,db.queryForObject("SELECT count(*) FROM stock_operations",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_balances b WHERE quantity<>(SELECT sum(quantity_delta) FROM stock_movements m WHERE m.warehouse_id=b.warehouse_id AND m.product_id=b.product_id)",Integer.class));
        assertEquals(200,request("GET","/api/inventory/movements?warehouse_id="+f.warehouse(),f.token(),null).statusCode());
        assertEquals(10.0,((Number)JsonPath.read(request("GET","/api/products/"+f.product(),f.token(),null).body(),"$.data.attributes.in_stock")).doubleValue(),0.00001);
    }
    @Test void concurrentDeductionsCannotMakeStockNegative() throws Exception {
        var f=stockFixture();postKey("/api/inventory/opening",f.token(),"race-opening",opening(f,10));
        var a=http.sendAsync(keyed("/api/inventory/adjustments",f.token(),"race-adjust-a",adjustment(f,-7)),HttpResponse.BodyHandlers.ofString());
        var b=http.sendAsync(keyed("/api/inventory/adjustments",f.token(),"race-adjust-b",adjustment(f,-7)),HttpResponse.BodyHandlers.ofString());
        var codes=java.util.stream.Stream.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode(),b.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode()).sorted().toList();
        assertEquals(java.util.List.of(200,409),codes);assertStock(f.warehouse(),f.product(),"3");assertEquals(2,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
    }
    @Test void consumedReceiptCannotBeCancelledAndBaseUnitIsLocked() throws Exception {
        var f=stockFixture();var created=postKey("/api/purchases",f.token(),"receipt-consumed",purchase(f,1,10));long id=((Number)JsonPath.read(created.body(),"$.data.id")).longValue();
        assertEquals(200,postKey("/api/inventory/adjustments",f.token(),"consume-stock",adjustment(f,-8)).statusCode());
        assertEquals(409,request("POST","/api/purchases/"+id+"/cancel",f.token(),"{}").statusCode());assertStock(f.warehouse(),f.product(),"2");
        assertEquals(1,db.queryForObject("SELECT status FROM purchases WHERE id=?",Integer.class,id));assertEquals(2,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        long base=create("base-units",f.token(),"{\"name\":\"Another base\"}"),unit=create("units",f.token(),"{\"name\":\"Another unit\",\"short_name\":\"x\",\"base_unit\":"+base+"}");
        assertEquals(409,request("PUT","/api/products/"+f.product(),f.token(),product(f.category(),f.brand(),base,unit,"STOCK001")).statusCode());
        db.update("UPDATE app_users SET role='VIEWER' WHERE email='admin@example.com'");
        try{assertEquals(403,postKey("/api/inventory/opening",f.token(),"denied-opening",opening(f,5)).statusCode());assertEquals(403,request("GET","/api/purchases",f.token(),null).statusCode());}
        finally{db.update("UPDATE app_users SET role='ADMIN' WHERE email='admin@example.com'");}
    }
    @Test void invalidPurchaseRollsBackIdempotencyClaimAndCanRetry() throws Exception {
        var f=stockFixture();String invalid=purchase(f,1,2).replace("\"quantity\":2","\"quantity\":0");
        assertEquals(422,postKey("/api/purchases",f.token(),"retry-after-error",invalid).statusCode());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM purchases",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM request_keys",Integer.class));
        assertEquals(201,postKey("/api/purchases",f.token(),"retry-after-error",purchase(f,1,2)).statusCode());
        assertEquals(422,request("POST","/api/purchases",f.token(),purchase(f,1,2)).statusCode());
    }
    @Test void draftBaseUnitChangeMustBeReviewedBeforeReceipt() throws Exception {
        var f=stockFixture();var created=postKey("/api/purchases",f.token(),"draft-unit-check",purchase(f,2,2));long id=((Number)JsonPath.read(created.body(),"$.data.id")).longValue();
        long base=create("base-units",f.token(),"{\"name\":\"Changed base\"}"),unit=create("units",f.token(),"{\"name\":\"Changed unit\",\"short_name\":\"new\",\"base_unit\":"+base+"}");
        assertEquals(200,request("PUT","/api/products/"+f.product(),f.token(),product(f.category(),f.brand(),base,unit,"STOCK001")).statusCode());
        assertEquals(409,request("POST","/api/purchases/"+id+"/receive",f.token(),"{}").statusCode());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        String revised=purchase(f,2,2).replace("\"purchase_unit\":"+f.unit(),"\"purchase_unit\":"+unit).replace("\"status\":2","\"status\":2,\"version\":0");
        assertEquals(200,request("PUT","/api/purchases/"+id,f.token(),revised).statusCode());
        assertEquals(200,request("POST","/api/purchases/"+id+"/receive",f.token(),"{}").statusCode());assertStock(f.warehouse(),f.product(),"2");
    }
    long saleCustomer(String token) throws Exception {
        return create("customers",token,"{\"name\":\"Buyer\",\"email\":\"buyer@example.com\",\"phone\":\"0501234567\",\"country\":\"SA\",\"city\":\"Riyadh\",\"address\":\"Street\"}");
    }
    String sale(StockFixture f,long customer,int status,int quantity) {
        return """
            {"customer_id":%d,"warehouse_id":%d,"date":"2026-11-14","status":%d,"grand_total":9999,
             "sale_items":[{"product_id":%d,"sale_unit":%d,"product_price":10,"quantity":%d,
             "discount_type":2,"discount_value":1,"tax_type":1,"tax_value":10}]}
            """.formatted(customer,f.warehouse(),status,f.product(),f.unit(),quantity);
    }
    @Test void saleDraftCompletionAndCancellationAreAuditedAndIdempotent() throws Exception {
        var f=stockFixture();long customer=saleCustomer(f.token());
        postKey("/api/inventory/opening",f.token(),"sale-opening",opening(f,10));
        var created=postKey("/api/sales",f.token(),"sale-draft-key",sale(f,customer,2,3));
        assertEquals(201,created.statusCode(),created.body());
        long id=((Number)JsonPath.read(created.body(),"$.data.id")).longValue();
        assertEquals(29.7,((Number)JsonPath.read(created.body(),"$.data.attributes.grand_total")).doubleValue(),0.00001);
        assertStock(f.warehouse(),f.product(),"10");
        String revised=sale(f,customer,2,4).replace("\"status\":2","\"status\":2,\"version\":0");
        assertEquals(200,request("PUT","/api/sales/"+id,f.token(),revised).statusCode());
        assertEquals(409,request("PUT","/api/sales/"+id,f.token(),revised).statusCode());
        assertEquals(200,request("POST","/api/sales/"+id+"/complete",f.token(),"{}").statusCode());
        assertEquals(200,request("POST","/api/sales/"+id+"/complete",f.token(),"{}").statusCode());
        assertStock(f.warehouse(),f.product(),"6");
        assertEquals(409,request("PUT","/api/sales/"+id,f.token(),revised).statusCode());
        assertEquals(200,request("POST","/api/sales/"+id+"/cancel",f.token(),"{}").statusCode());
        assertEquals(200,request("POST","/api/sales/"+id+"/cancel",f.token(),"{}").statusCode());
        assertStock(f.warehouse(),f.product(),"10");
        assertEquals(409,request("POST","/api/sales/"+id+"/complete",f.token(),"{}").statusCode());
        assertEquals(2,db.queryForObject("SELECT count(*) FROM stock_operations WHERE sale_id=?",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_operations WHERE sale_id IS NOT NULL AND purchase_id IS NOT NULL",Integer.class));
    }
    @Test void competingSalesCannotOversellAndFailedSaleFullyRollsBack() throws Exception {
        var f=stockFixture();long customer=saleCustomer(f.token());postKey("/api/inventory/opening",f.token(),"sale-race-opening",opening(f,10));
        var a=http.sendAsync(keyed("/api/sales",f.token(),"sale-race-a",sale(f,customer,1,7)),HttpResponse.BodyHandlers.ofString());
        var b=http.sendAsync(keyed("/api/sales",f.token(),"sale-race-b",sale(f,customer,1,7)),HttpResponse.BodyHandlers.ofString());
        var codes=java.util.stream.Stream.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode(),b.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode()).sorted().toList();
        assertEquals(java.util.List.of(201,409),codes);assertStock(f.warehouse(),f.product(),"3");
        assertEquals(1,db.queryForObject("SELECT count(*) FROM sales",Integer.class));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM sale_items",Integer.class));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM request_keys WHERE scope LIKE '%:sales'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_balances b WHERE quantity<>(SELECT sum(quantity_delta) FROM stock_movements m WHERE m.warehouse_id=b.warehouse_id AND m.product_id=b.product_id)",Integer.class));
    }
    @Test void duplicateConcurrentSalesUseOneDocumentAndMovement() throws Exception {
        var f=stockFixture();long customer=saleCustomer(f.token());postKey("/api/inventory/opening",f.token(),"sale-duplicate-opening",opening(f,10));
        var a=http.sendAsync(keyed("/api/sales",f.token(),"same-sale-key",sale(f,customer,1,3)),HttpResponse.BodyHandlers.ofString());
        var b=http.sendAsync(keyed("/api/sales",f.token(),"same-sale-key",sale(f,customer,1,3)),HttpResponse.BodyHandlers.ofString());
        var ra=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var rb=b.get(20,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(201,ra.statusCode(),ra.body());assertEquals(201,rb.statusCode(),rb.body());
        assertEquals((Object)JsonPath.read(ra.body(),"$.data.id"),(Object)JsonPath.read(rb.body(),"$.data.id"));
        assertStock(f.warehouse(),f.product(),"7");
        assertEquals(409,postKey("/api/sales",f.token(),"same-sale-key",sale(f,customer,1,4)).statusCode());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM stock_operations WHERE kind='SALE'",Integer.class));
    }
    @Test void salesBlockPurchaseCancellationUntilSoldStockIsRestored() throws Exception {
        var f=stockFixture();long customer=saleCustomer(f.token());
        var receipt=postKey("/api/purchases",f.token(),"mixed-purchase",purchase(f,1,10));long purchaseId=((Number)JsonPath.read(receipt.body(),"$.data.id")).longValue();
        var sold=postKey("/api/sales",f.token(),"mixed-sale",sale(f,customer,1,7));assertEquals(201,sold.statusCode(),sold.body());long saleId=((Number)JsonPath.read(sold.body(),"$.data.id")).longValue();
        assertEquals(409,request("POST","/api/purchases/"+purchaseId+"/cancel",f.token(),"{}").statusCode());
        assertStock(f.warehouse(),f.product(),"3");
        assertEquals(200,request("POST","/api/sales/"+saleId+"/cancel",f.token(),"{}").statusCode());
        assertEquals(200,request("POST","/api/purchases/"+purchaseId+"/cancel",f.token(),"{}").statusCode());assertStock(f.warehouse(),f.product(),"0");
    }
    @Test void invalidSaleCanRetryAndPaymentsAndUnauthorizedWritesAreRejected() throws Exception {
        var f=stockFixture();long customer=saleCustomer(f.token());
        assertEquals(422,postKey("/api/sales",f.token(),"invalid-sale-retry",sale(f,customer,2,0)).statusCode());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM request_keys",Integer.class));
        assertEquals(201,postKey("/api/sales",f.token(),"invalid-sale-retry",sale(f,customer,2,2)).statusCode());
        assertEquals(422,request("POST","/api/sales",f.token(),sale(f,customer,2,2)).statusCode());
        String paid=sale(f,customer,2,2).replace("\"grand_total\":9999","\"paid_amount\":5");
        assertEquals(422,postKey("/api/sales",f.token(),"unsupported-payment",paid).statusCode());
        db.update("UPDATE app_users SET role='VIEWER' WHERE email='admin@example.com'");
        try {
            assertEquals(403,request("GET","/api/sales",f.token(),null).statusCode());
            assertEquals(403,postKey("/api/sales",f.token(),"unauthorized-sale",sale(f,customer,2,2)).statusCode());
        } finally { db.update("UPDATE app_users SET role='ADMIN' WHERE email='admin@example.com'"); }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM sales",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
    }
    @Test void multiLineSaleFailureDoesNotDeductAnyProduct() throws Exception {
        var f=stockFixture();long customer=saleCustomer(f.token());postKey("/api/inventory/opening",f.token(),"multi-sale-opening",opening(f,10));
        long second=create("products",f.token(),product(f.category(),f.brand(),f.base(),f.unit(),"STOCK002"));
        String body=sale(f,customer,1,2).replace("}]}","},{\"product_id\":"+second+",\"product_price\":10,\"quantity\":1}]}");
        var result=postKey("/api/sales",f.token(),"multi-sale-failed",body);
        assertEquals(409,result.statusCode(),result.body());assertStock(f.warehouse(),f.product(),"10");
        assertEquals(0,db.queryForObject("SELECT count(*) FROM sales",Integer.class));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM stock_movements",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM stock_balances WHERE product_id=?",Integer.class,second));
    }
    @Test void migrationFromVersionThreePreservesExistingWarehouseAndAddsSalesPermission() {
        var first=org.flywaydb.core.Flyway.configure().dataSource(postgres.getPostgresDatabase()).schemas("sales_upgrade_test").defaultSchema("sales_upgrade_test").target("3").load();
        first.migrate();
        try {
            db.update("INSERT INTO sales_upgrade_test.warehouses(name,phone,country,city) VALUES ('Existing warehouse','123','SA','Riyadh')");
            var upgraded=org.flywaydb.core.Flyway.configure().dataSource(postgres.getPostgresDatabase()).schemas("sales_upgrade_test").defaultSchema("sales_upgrade_test").load();
            upgraded.migrate();
            assertEquals(1,db.queryForObject("SELECT count(*) FROM sales_upgrade_test.warehouses",Integer.class));
            assertEquals(0,db.queryForObject("SELECT count(*) FROM sales_upgrade_test.sales",Integer.class));
            assertEquals(1,db.queryForObject("SELECT count(*) FROM sales_upgrade_test.role_permissions rp JOIN sales_upgrade_test.roles r ON r.id=rp.role_id JOIN sales_upgrade_test.permissions p ON p.id=rp.permission_id WHERE r.name='ADMIN' AND p.name='manage_sale'",Integer.class));
        } finally { db.execute("DROP SCHEMA sales_upgrade_test CASCADE"); }
    }
}
