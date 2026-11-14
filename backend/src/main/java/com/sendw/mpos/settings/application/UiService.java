package com.sendw.mpos.settings.application;
import com.sendw.mpos.identity.application.IdentityService;
import com.sendw.mpos.catalog.application.CatalogService;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.identity.application.Permissions;
import java.util.*;
import com.sendw.mpos.warehouses.application.WarehouseService;

@org.springframework.stereotype.Service
public class UiService {
    private final IdentityService identity;private final SettingsRepository repository;private final CatalogService catalog;private final WarehouseService warehouses;
    public UiService(IdentityService identity,SettingsRepository repository,CatalogService catalog,WarehouseService warehouses){this.identity=identity;this.repository=repository;this.catalog=catalog;this.warehouses=warehouses;}
    Map<String,String> settings(){
        return repository.settings();
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
            repository.update(key,value);
        }
        return Map.of("data",settings());
    }
     public Map<String,Object> config(){
        return Map.of("data",Map.of("permissions",identity.permissions(identity.current().role()),"version","0.2.0","date_format",settings().get("date_format"),"is_version","false","is_currency_right","false","open_register",true,"migration_mode",true));
    }
     public Map<String,Object> language(LanguageChange b){
        String language=b.language();if(!Set.of("en","ar").contains(language==null?"":language))throw Validation.invalid("Supported languages are en and ar");
        identity.changeLanguage(language);return Map.of("success",true);
    }
     public Map<String,Object> summary(){
        var permissions=identity.permissions(identity.current().role());var values=catalog.summary(permissions);
        if(permissions.contains("manage_warehouses"))values.put("warehouses",warehouses.count());
        return Map.of("data",values);
    }
}
