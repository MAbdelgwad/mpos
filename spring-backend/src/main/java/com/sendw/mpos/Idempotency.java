package com.sendw.mpos;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
@Service
public class Idempotency {
    private final JdbcTemplate db;private final ObjectMapper json;private final IdentityService identity;
    public Idempotency(JdbcTemplate db,ObjectMapper json,IdentityService identity){this.db=db;this.json=json;this.identity=identity;}
    // Caller must be transactional: the claim, side effects, and response commit together.
    public Map<String,Object> execute(String scope,String key,Map<String,Object> body,Supplier<Map<String,Object>> action){
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}"))throw CatalogService.invalid("Idempotency-Key (8–128 letters/digits/_/-) is required");
        scope=identity.current().id()+":"+scope;
        String fingerprint=IdentityService.digest(json.writeValueAsString(canonical(body)));
        int inserted=db.update("INSERT INTO request_keys(scope,key,fingerprint) VALUES (?,?,?) ON CONFLICT DO NOTHING",scope,key,fingerprint);
        if(inserted==0){var row=db.queryForMap("SELECT fingerprint,response::text FROM request_keys WHERE scope=? AND key=? FOR UPDATE",scope,key);
            if(!fingerprint.equals(row.get("fingerprint")))throw Requests.conflict("Idempotency key was already used with different data");
            @SuppressWarnings("unchecked") Map<String,Object> response=json.readValue((String)row.get("response"),Map.class);return response;
        }
        var response=action.get();db.update("UPDATE request_keys SET response=?::jsonb WHERE scope=? AND key=?",json.writeValueAsString(response),scope,key);return response;
    }
    private Object canonical(Object value){if(value instanceof Map<?,?> m){var map=new TreeMap<String,Object>();m.forEach((k,v)->map.put(k.toString(),canonical(v)));return map;}if(value instanceof List<?> list)return list.stream().map(this::canonical).toList();return value;}
}
