package com.sendw.mpos.settings.application;
import com.sendw.mpos.identity.application.IdentityService;
import com.sendw.mpos.catalog.application.CatalogService;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.identity.application.Permissions;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.stereotype.Service
public class UiService {
    private final IdentityService identity;private final JdbcTemplate db;
    public UiService(IdentityService identity,JdbcTemplate db){this.identity=identity;this.db=db;}
    Map<String,String> settings(){
        var result=new LinkedHashMap<String,String>();db.queryForList("SELECT key,value FROM ui_settings").forEach(row->result.put((String)row.get("key"),(String)row.get("value")));return result;
    }
     public Map<String,Object> front(){
        var values=settings();values.putAll(Map.of("logo","/images/logo.png","developed","","warehouse_name","","customer_name",""));
        return Map.of("data",Map.of("type","settings","value",values));
    }
     public Map<String,Object> getSettings(){Permissions.require("manage_setting");return Map.of("data",settings());}
     @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> updateSettings( Map<String,String> body){
        Permissions.require("manage_setting");var allowed=settings().keySet();
        for(var entry:body.entrySet()) {
            String key=entry.getKey(),value=entry.getValue();
            if(!allowed.contains(key) || value==null || value.length()>500)throw Validation.invalid("Invalid setting");
            if(key.equals("company_name") && value.isBlank())throw Validation.invalid("Company name is required");
            if(key.equals("default_language") && !Set.of("ar","en").contains(value))throw Validation.invalid("Invalid language");
            if(key.equals("date_format") && !Set.of("d-m-y","m-d-y","y-m-d").contains(value))throw Validation.invalid("Invalid date format");
            db.update("UPDATE ui_settings SET value=? WHERE key=?",value,key);
        }
        return Map.of("data",settings());
    }
     public Map<String,Object> config(){
        return Map.of("data",Map.of("permissions",identity.permissions(identity.current().role()),"version","0.2.0","date_format",settings().get("date_format"),"is_version","false","is_currency_right","false","open_register",true,"migration_mode",true));
    }
     public Map<String,Object> language( Map<String,String> b){
        String language=b.get("language");if(!Set.of("en","ar").contains(language==null?"":language))throw Validation.invalid("Supported languages are en and ar");
        db.update("UPDATE app_users SET language=? WHERE id=?",language,identity.current().id());return Map.of("success",true);
    }
     public Map<String,Object> summary(){
        var values=new LinkedHashMap<String,Object>();var permissions=identity.permissions(identity.current().role());
        CatalogService.SPECS.forEach((kind,s)->{if(permissions.contains(s.permission()))values.put(kind,db.queryForObject("SELECT count(*) FROM "+s.table(),Long.class));});
        if(permissions.contains("manage_warehouses"))values.put("warehouses",db.queryForObject("SELECT count(*) FROM warehouses",Long.class));
        return Map.of("data",values);
    }
}
