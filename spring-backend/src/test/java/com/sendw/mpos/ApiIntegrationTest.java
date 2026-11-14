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
    @BeforeEach void clear() { db.update("DELETE FROM access_tokens"); db.update("DELETE FROM warehouses"); }
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
}
