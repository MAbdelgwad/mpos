package com.sendw.mpos;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class UiController {
    private final IdentityService identity;private final JdbcTemplate db;
    public UiController(IdentityService identity,JdbcTemplate db){this.identity=identity;this.db=db;}
    Map<String,String> settings(){
        var result=new LinkedHashMap<String,String>();db.queryForList("SELECT key,value FROM ui_settings").forEach(row->result.put((String)row.get("key"),(String)row.get("value")));return result;
    }
    @GetMapping("/front-setting") public Map<String,Object> front(){
        var values=settings();values.putAll(Map.of("logo","/images/logo.png","developed","","warehouse_name","","customer_name",""));
        return Map.of("data",Map.of("type","settings","value",values));
    }
    @GetMapping("/ui-settings") public Map<String,Object> getSettings(){CatalogService.require("manage_setting");return Map.of("data",settings());}
    @PutMapping("/ui-settings") @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> updateSettings(@RequestBody Map<String,String> body){
        CatalogService.require("manage_setting");var allowed=settings().keySet();
        for(var entry:body.entrySet()) {
            String key=entry.getKey(),value=entry.getValue();
            if(!allowed.contains(key) || value==null || value.length()>500)throw CatalogService.invalid("Invalid setting");
            if(key.equals("company_name") && value.isBlank())throw CatalogService.invalid("Company name is required");
            if(key.equals("default_language") && !Set.of("ar","en").contains(value))throw CatalogService.invalid("Invalid language");
            if(key.equals("date_format") && !Set.of("d-m-y","m-d-y","y-m-d").contains(value))throw CatalogService.invalid("Invalid date format");
            db.update("UPDATE ui_settings SET value=? WHERE key=?",value,key);
        }
        return Map.of("data",settings());
    }
    @GetMapping("/config") public Map<String,Object> config(){
        return Map.of("data",Map.of("permissions",identity.permissions(identity.current().role()),"version","0.2.0","date_format",settings().get("date_format"),"is_version","false","is_currency_right","false","open_register",true,"migration_mode",true));
    }
    @PostMapping("/change-language") public Map<String,Object> language(@RequestBody Map<String,String> b){
        String language=b.get("language");if(!Set.of("en","ar").contains(language==null?"":language))throw CatalogService.invalid("Supported languages are en and ar");
        db.update("UPDATE app_users SET language=? WHERE id=?",language,identity.current().id());return Map.of("success",true);
    }
    @GetMapping("/catalog-summary") public Map<String,Object> summary(){
        var values=new LinkedHashMap<String,Object>();var permissions=identity.permissions(identity.current().role());
        CatalogService.SPECS.forEach((kind,s)->{if(permissions.contains(s.permission()))values.put(kind,db.queryForObject("SELECT count(*) FROM "+s.table(),Long.class));});
        if(permissions.contains("manage_warehouses"))values.put("warehouses",db.queryForObject("SELECT count(*) FROM warehouses",Long.class));
        return Map.of("data",values);
    }
}
