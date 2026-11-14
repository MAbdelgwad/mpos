package com.sendw.mpos;

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
    @BeforeEach void clear() { db.update("DELETE FROM access_tokens"); db.update("DELETE FROM warehouses"); db.execute("TRUNCATE products, units, base_units, brands, product_categories, customers, suppliers, media CASCADE"); db.update("DELETE FROM app_users WHERE email<>'admin@example.com'"); db.update("DELETE FROM roles WHERE protected_role=false"); }
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
        assertEquals(401,request("GET","/api/warehouses",null,null).statusCode());
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
        var process=new ProcessBuilder("node","../spring-frontend/tests/smoke.cjs",String.valueOf(port)).inheritIO().start();
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
}
