package com.sendw.mpos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final JdbcTemplate db;
    public WarehouseController(JdbcTemplate db) { this.db=db; }
    public record WarehouseInput(
        @NotBlank @Size(max=255) String name,
        @NotBlank @Pattern(regexp="[0-9+() -]{1,40}") String phone,
        @NotBlank @Size(max=100) String country,
        @NotBlank @Size(max=100) String city,
        @Email @Size(max=254) String email,
        @Size(max=30) String zip_code) {}

    @GetMapping
    public Map<String,Object> list(
            @RequestParam(name="page[number]", defaultValue="1") int page,
            @RequestParam(name="page[size]", defaultValue="10") int size,
            @RequestParam(name="filter[search]", defaultValue="") String search,
            @RequestParam(defaultValue="-created_at") String sort) {
        if(page < 1 || size < 0 || size > 100 || search.length() > 255) throw bad("Invalid pagination or search");
        boolean descending=sort.startsWith("-");
        String column=descending ? sort.substring(1) : sort;
        if(!Set.of("id","name","phone","country","city","email","zip_code","created_at","updated_at").contains(column)) throw bad("Unsupported sort field");
        // A size of zero is the existing React dropdown convention for all records.
        String where=" WHERE name ILIKE ? OR phone ILIKE ? OR country ILIKE ? OR city ILIKE ? OR email ILIKE ?";
        String pattern="%" + search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
        Object[] params={pattern,pattern,pattern,pattern,pattern};
        long total=db.queryForObject("SELECT count(*) FROM warehouses"+where,Long.class,params);
        String sql="SELECT * FROM warehouses"+where+" ORDER BY "+column+(descending?" DESC":" ASC")+", id ASC";
        List<Object> values=new ArrayList<>(Arrays.asList(params));
        if(size>0) { sql+=" LIMIT ? OFFSET ?"; values.add(size); values.add(((long)page-1)*size); }
        var data=db.queryForList(sql,values.toArray()).stream().map(this::resource).toList();
        return Map.of("data",data,"meta",Map.of("total",total,"current_page",size==0?1:page,"per_page",size==0?total:size,
                "last_page",size==0?1:Math.max(1,(total+size-1)/size)));
    }

    @GetMapping("/{id}")
    public Map<String,Object> show(@PathVariable long id) { return Map.of("data",resource(find(id))); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> create(@Valid @RequestBody WarehouseInput input) {
        Long id=db.queryForObject("""
            INSERT INTO warehouses(name,phone,country,city,email,zip_code) VALUES (?,?,?,?,?,?) RETURNING id
            """,Long.class, input.name().trim(),input.phone(),input.country().trim(),input.city().trim(),nullable(input.email()),nullable(input.zip_code()));
        return show(id);
    }
    @RequestMapping(value="/{id}",method={RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.POST})
    @Transactional
    public Map<String,Object> update(@PathVariable long id, @Valid @RequestBody WarehouseInput input) {
        int count=db.update("""
            UPDATE warehouses SET name=?,phone=?,country=?,city=?,email=?,zip_code=?,updated_at=now() WHERE id=?
            """,input.name().trim(),input.phone(),input.country().trim(),input.city().trim(),nullable(input.email()),nullable(input.zip_code()),id);
        if(count==0) throw missing();
        return show(id);
    }
    @DeleteMapping("/{id}")
    public Map<String,Object> delete(@PathVariable long id) {
        if(db.update("DELETE FROM warehouses WHERE id=?",id)==0) throw missing();
        return Map.of("success",true,"message","Warehouse deleted successfully");
    }
    private Map<String,Object> find(long id) {
        var rows=db.queryForList("SELECT * FROM warehouses WHERE id=?",id);
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
    private static String nullable(String value) { return value==null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse not found"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
