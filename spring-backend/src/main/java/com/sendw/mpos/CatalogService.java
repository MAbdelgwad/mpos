package com.sendw.mpos;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CatalogService {
    record Spec(String table,String permission,Set<String> fields,Set<String> required) {}
    static Spec spec(String table,String permission,String fields,String required) {
        return new Spec(table,permission,new LinkedHashSet<>(List.of(fields.split(","))),Set.of(required.split(",")));
    }
    static final Map<String,Spec> SPECS=Map.of(
        "brands",spec("brands","manage_brands","name,description","name"),
        "product-categories",spec("product_categories","manage_product_categories","name,description","name"),
        "base-units",spec("base_units","manage_units","name","name"),
        "units",spec("units","manage_units","name,short_name,base_unit","name,short_name,base_unit"),
        "customers",spec("customers","manage_customers","name,email,phone,country,city,address,dob","name,email,phone,country,city,address"),
        "suppliers",spec("suppliers","manage_suppliers","name,email,phone,country,city,address","name,email,phone,country,city,address"),
        "products",spec("products","manage_products","name,code,product_category_id,brand_id,product_cost,product_price,product_unit,sale_unit,purchase_unit,stock_alert,quantity_limit,order_tax,tax_type,barcode_symbol,notes","name,code,product_category_id,brand_id,product_cost,product_price,product_unit,barcode_symbol"));
    private final JdbcTemplate db;
    public CatalogService(JdbcTemplate db) {this.db=db;}
    Spec access(String kind) {
        Spec spec=SPECS.get(kind);
        if(spec==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown resource");
        require(spec.permission());return spec;
    }
    static void require(String permission) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(permission)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Forbidden");
    }
    static ResponseStatusException invalid(String message) {return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
    static ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");}
    public Map<String,Object> list(String kind,Map<String,String> query) {
        Spec s=access(kind);
        int size=integer(query.getOrDefault("page[size]","10"));int page=integer(query.getOrDefault("page[number]","1"));
        if(size<0 || size>100 || page<1) throw invalid("Invalid pagination");
        String sort=query.getOrDefault("sort","-created_at");boolean desc=sort.startsWith("-");String column=desc?sort.substring(1):sort;
        if(!s.fields().contains(column) && !Set.of("id","created_at","updated_at").contains(column)) throw invalid("Unsupported sort field");
        String search=query.getOrDefault("filter[search]","");if(search.length()>255) throw invalid("Search too long");
        String pattern="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String where=" WHERE name ILIKE ?";List<Object> args=new ArrayList<>();args.add(pattern);
        for(String f:List.of("base_unit","brand_id","product_category_id","product_unit")) {
            String value=query.getOrDefault("filter["+f+"]",query.get(f));
            if(value!=null && !value.isBlank() && s.fields().contains(f)) {where+=" AND "+f+"=?";args.add(positiveId(value));}
        }
        long total=db.queryForObject("SELECT count(*) FROM "+s.table()+where,Long.class,args.toArray());
        String sql="SELECT * FROM "+s.table()+where+" ORDER BY "+column+(desc?" DESC":" ASC")+",id";
        if(size>0) {sql+=" LIMIT ? OFFSET ?";args.add(size);args.add(((long)page-1)*size);}
        var data=db.queryForList(sql,args.toArray()).stream().map(row->resource(kind,row)).toList();
        return Map.of("data",data,"meta",Map.of("total",total,"current_page",size==0?1:page,"per_page",size==0?total:size,"last_page",size==0?1:Math.max(1,(total+size-1)/size)));
    }
    public Map<String,Object> show(String kind,long id) {Spec s=access(kind);return Map.of("data",resource(kind,find(s.table(),id)));}
    Map<String,Object> find(String table,long id) {
        var rows=db.queryForList("SELECT * FROM "+table+" WHERE id=?",id);
        if(rows.isEmpty())throw missing();return rows.getFirst();
    }
    static int integer(String s) {try{return Integer.parseInt(s);}catch(Exception e){throw invalid("Invalid integer");}}
    static long positiveId(Object v) {if(v==null)throw invalid("Missing relation ID");try{long id=Long.parseLong(v.toString());if(id<1)throw invalid("Invalid relation ID");return id;}catch(NumberFormatException e){throw invalid("Invalid relation ID");}}
    Map<String,Object> validate(String kind,Map<String,Object> body) {
        Spec s=SPECS.get(kind);Map<String,Object> out=new LinkedHashMap<>();
        if(body.containsKey("purchase_quantity") && !String.valueOf(body.get("purchase_quantity")).isBlank()) throw invalid("Opening stock must be entered through the inventory workflow");
        for(String field:s.fields()) {
            Object value=body.get(field);String text=value==null?"":value.toString().trim();
            if(s.required().contains(field) && text.isEmpty()) throw invalid(field+" is required");
            Object normalized=text.isEmpty()?null:text;
            if(text.length()> (Set.of("notes","description","address").contains(field)?10000:255)) throw invalid(field+" is too long");
            if(field.equals("email") && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Invalid email");
            if(field.equals("email")) normalized=text.toLowerCase(Locale.ROOT);
            if(field.equals("phone") && !text.matches("[0-9+() -]{1,40}")) throw invalid("Invalid phone");
            if(!text.isEmpty() && Set.of("base_unit","product_category_id","brand_id","product_unit","sale_unit","purchase_unit").contains(field)) normalized=positiveId(text);
            if(Set.of("product_cost","product_price","stock_alert","quantity_limit","order_tax").contains(field)) {
                try {
                    BigDecimal number=text.isEmpty()? (field.equals("quantity_limit")?null:BigDecimal.ZERO):new BigDecimal(text);
                    if(number!=null && (number.signum()<0 || number.scale()>4 || number.precision()-number.scale()>15 || (field.equals("order_tax") && number.compareTo(new BigDecimal("100"))>0))) throw invalid("Invalid "+field);
                    normalized=number;
                } catch(NumberFormatException e) {throw invalid("Invalid "+field);}
            }
            if(field.equals("tax_type") || field.equals("barcode_symbol")) {
                int n=text.isEmpty()?1:integer(text);if(n<1 || n>(field.equals("tax_type")?2:5))throw invalid("Invalid "+field);normalized=n;
            }
            if(field.equals("dob") && !text.isEmpty()) {try{normalized=java.sql.Date.valueOf(LocalDate.parse(text));}catch(Exception e){throw invalid("Invalid birth date");}}
            out.put(field,normalized);
        }
        if(kind.equals("products")) {
            Barcodes.image((String)out.get("code"),(Integer)out.get("barcode_symbol"));
            for(String f:List.of("sale_unit","purchase_unit")) if(out.get(f)!=null) {
                var unit=find("units",((Number)out.get(f)).longValue());
                if(!unit.get("base_unit").equals(out.get("product_unit")))throw invalid(f+" must belong to the product base unit");
            }
        }
        return out;
    }
    @Transactional
    public Map<String,Object> save(String kind,Long id,Map<String,Object> body) {
        Spec s=access(kind);var values=validate(kind,body);
        if(id==null) {
            String fields=String.join(",",values.keySet());String placeholders=String.join(",",Collections.nCopies(values.size(),"?"));
            id=db.queryForObject("INSERT INTO "+s.table()+"("+fields+") VALUES ("+placeholders+") RETURNING id",Long.class,values.values().toArray());
        } else {
            find(s.table(),id);
            if(kind.equals("units")) {
                long count=db.queryForObject("SELECT count(*) FROM products WHERE (sale_unit=? OR purchase_unit=?) AND product_unit<>?",Long.class,id,id,values.get("base_unit"));
                if(count>0)throw invalid("Unit is used by products with a different base unit");
            }
            var args=new ArrayList<>(values.values());args.add(id);
            db.update("UPDATE "+s.table()+" SET "+String.join(",",values.keySet().stream().map(f->f+"=?").toList())+",updated_at=now() WHERE id=?",args.toArray());
        }
        return show(kind,id);
    }
    @Transactional
    public void delete(String kind,long id) {
        Spec s=access(kind);if(db.update("DELETE FROM "+s.table()+" WHERE id=?",id)==0)throw missing();
        db.update("DELETE FROM media WHERE owner_type=? AND owner_id=?",kind,id);
    }
    Map<String,Object> resource(String kind,Map<String,Object> row) {
        var a=new LinkedHashMap<>(row);Object id=a.remove("id");
        a.replaceAll((key,value)->value instanceof Timestamp t?t.toInstant().toString():value instanceof java.sql.Date d?d.toString():value);
        if(kind.equals("units"))a.put("base_unit_name",find("base_units",((Number)a.get("base_unit")).longValue()));
        var media=db.queryForList("SELECT id FROM media WHERE owner_type=? AND owner_id=? ORDER BY id",kind,id);
        List<String> urls=media.stream().map(x->"/api/media/"+x.get("id")).toList();
        if(kind.equals("brands") || kind.equals("product-categories")) {
            a.put("image",urls.isEmpty()?"":urls.getFirst());
            String foreign=kind.equals("brands")?"brand_id":"product_category_id";
            a.put("product_count",db.queryForObject("SELECT count(*) FROM products WHERE "+foreign+"=?",Long.class,id));
        }
        if(kind.equals("products")) {
            a.put("product_category_name",find("product_categories",((Number)a.get("product_category_id")).longValue()).get("name"));
            a.put("brand_name",find("brands",((Number)a.get("brand_id")).longValue()).get("name"));
            a.put("product_unit_name",find("base_units",((Number)a.get("product_unit")).longValue()));
            for(String f:List.of("sale_unit","purchase_unit"))a.put(f+"_name",a.get(f)==null?"":find("units",((Number)a.get(f)).longValue()));
            a.put("images",Map.of("imageUrls",urls,"id",media.stream().map(x->x.get("id").toString()).toList()));
            String barcode=Barcodes.image((String)a.get("code"),((Number)a.get("barcode_symbol")).intValue());
            a.put("barcode_image_url",barcode);a.put("barcode_url",barcode);
            a.put("in_stock",0);a.put("stock",Map.of("quantity",0));a.put("warehouse",List.of());
        }
        return Map.of("type",kind,"id",id,"attributes",a,"links",Map.of("self","/api/"+kind+"/"+id));
    }
}
