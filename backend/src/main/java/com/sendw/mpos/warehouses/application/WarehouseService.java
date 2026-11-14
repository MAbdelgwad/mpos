package com.sendw.mpos.warehouses.application;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Service
public class WarehouseService {
    private final WarehouseRepository repository;
    public WarehouseService(WarehouseRepository repository) { this.repository=repository; }



    public Map<String,Object> list(
             int page,
             int size,
             String search,
             String sort) {
        if(page < 1 || size < 0 || size > 100 || search.length() > 255) throw bad("Invalid pagination or search");
        boolean descending=sort.startsWith("-");
        String column=descending ? sort.substring(1) : sort;
        if(!Set.of("id","name","phone","country","city","email","zip_code","created_at","updated_at").contains(column)) throw bad("Unsupported sort field");
        // A size of zero is the existing React dropdown convention for all records.
        String pattern="%" + search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
        var result=repository.search(pattern,column,descending,page,size);long total=result.total();
        var data=result.rows().stream().map(this::resource).toList();
        return Map.of("data",data,"meta",Map.of("total",total,"current_page",size==0?1:page,"per_page",size==0?total:size,
                "last_page",size==0?1:Math.max(1,(total+size-1)/size)));
    }


    public Map<String,Object> show( long id) { return Map.of("data",resource(find(id))); }


    @Transactional
    public Map<String,Object> create(  WarehouseInput input) {
        long id=repository.insert(normalize(input));
        return show(id);
    }

    @Transactional
    public Map<String,Object> update( long id,   WarehouseInput input) {
        int count=repository.update(id,normalize(input));
        if(count==0) throw missing();
        return show(id);
    }

    public Map<String,Object> delete( long id) {
        if(repository.delete(id)==0) throw missing();
        return Map.of("success",true,"message","Warehouse deleted successfully");
    }
    private Map<String,Object> find(long id) {
        var rows=repository.find(id);
        if(rows.isEmpty()) throw missing(); return rows.getFirst();
    }
    private Map<String,Object> resource(Map<String,Object> row) {
        var attributes=new LinkedHashMap<>(row); Object id=attributes.remove("id");
        for(String field:List.of("created_at","updated_at")) {
            Object value=attributes.get(field);
            if(value instanceof java.sql.Timestamp ts) attributes.put(field,ts.toInstant().toString());
        }
        return Map.of("type","warehouses","id",id,"attributes",attributes,"links",Map.of("self","/api/warehouses/"+id));
    }
    public long count() { com.sendw.mpos.identity.application.Permissions.require("manage_warehouses");return repository.count(); }
    private WarehouseInput normalize(WarehouseInput input) { return new WarehouseInput(input.name().trim(),input.phone(),input.country().trim(),input.city().trim(),nullable(input.email()),nullable(input.zip_code())); }
    private static String nullable(String value) { return value==null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
