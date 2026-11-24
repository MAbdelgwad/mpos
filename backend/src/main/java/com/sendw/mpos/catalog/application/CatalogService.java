package com.sendw.mpos.catalog.application;
import com.sendw.mpos.shared.domain.Requests;

import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.identity.application.Permissions;
import static com.sendw.mpos.shared.domain.Validation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import com.sendw.mpos.catalog.domain.CatalogSchema.Spec;
import static com.sendw.mpos.catalog.domain.CatalogSchema.SPECS;
import com.sendw.mpos.media.application.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CatalogService {
    private final CatalogRepository repository;
    private final BarcodeRenderer barcodes;
    private final MediaService images;
    public CatalogService(CatalogRepository repository,BarcodeRenderer barcodes,MediaService images) {this.repository=repository;this.barcodes=barcodes;this.images=images;}
    public Map<String,Object> summary(List<String> permissions) {
        var result=new LinkedHashMap<String,Object>();
        SPECS.forEach((kind,spec)->{if(permissions.contains(spec.permission()))result.put(kind,repository.count(spec));});
        return result;
    }
    Spec access(String kind) {
        Spec spec=SPECS.get(kind);
        if(spec==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown resource");
        Permissions.require(spec.permission());return spec;
    }

    public Map<String,Object> list(String kind,Map<String,String> query) {
        Spec s=access(kind);
        int size=integer(query.getOrDefault("page[size]","10"));int page=integer(query.getOrDefault("page[number]","1"));
        if(size<0 || size>100 || page<1) throw invalid("Invalid pagination");
        String sort=query.getOrDefault("sort","-created_at");boolean desc=sort.startsWith("-");String column=desc?sort.substring(1):sort;
        if(!s.fields().contains(column) && !Set.of("id","created_at","updated_at").contains(column)) throw invalid("Unsupported sort field");
        String search=query.getOrDefault("filter[search]","");if(search.length()>255) throw invalid("Search too long");
        String pattern="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        var filters=new LinkedHashMap<String,Long>();
        for(String f:List.of("brand_id","product_category_id","product_unit")) {
            String value=query.getOrDefault("filter["+f+"]",query.get(f));
            if(value!=null&&!value.isBlank()&&s.fields().contains(f))filters.put(f,positiveId(value));
        }
        var result=repository.search(s,pattern,filters,column,desc,page,size);long total=result.total();
        var data=result.rows().stream().map(row->resource(kind,row)).toList();
        return Map.of("data",data,"meta",Map.of("total",total,"current_page",size==0?1:page,"per_page",size==0?total:size,"last_page",size==0?1:Math.max(1,(total+size-1)/size)));
    }
    public Map<String,Object> show(String kind,long id) {Spec s=access(kind);return Map.of("data",resource(kind,find(s.table(),id)));}
    Map<String,Object> find(String table,long id) {
        var rows=repository.find(table,id);
        if(rows.isEmpty())throw missing();return rows.getFirst();
    }

    Map<String,Object> validate(String kind,Map<String,Object> body) {
        Spec s=SPECS.get(kind);Map<String,Object> out=new LinkedHashMap<>();
        if(body.containsKey("base_unit"))throw invalid("Base units have been removed; reload the application");
        if(body.containsKey("purchase_quantity") && !String.valueOf(body.get("purchase_quantity")).isBlank()) throw invalid("Opening stock must be entered through the inventory workflow");
        for(String field:s.fields()) {
            Object value=body.get(field);String text=value==null?"":value.toString().trim();
            if(s.required().contains(field) && text.isEmpty()) throw invalid(field+" is required");
            Object normalized=text.isEmpty()?null:text;
            if(text.length()> (Set.of("notes","description","address").contains(field)?10000:255)) throw invalid(field+" is too long");
            if(field.equals("short_name") && text.length()>50)throw invalid("Abbreviation is too long");
            if(field.equals("email") && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Invalid email");
            if(field.equals("email")) normalized=text.toLowerCase(Locale.ROOT);
            if(field.equals("phone") && !text.matches("[0-9+() -]{1,40}")) throw invalid("Invalid phone");
            if(!text.isEmpty() && Set.of("product_category_id","brand_id","product_unit","sale_unit","purchase_unit").contains(field)) normalized=positiveId(text);
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
            barcodes.image((String)out.get("code"),(Integer)out.get("barcode_symbol"));
            find("units",((Number)out.get("product_unit")).longValue());
            for(String field:List.of("sale_unit","purchase_unit")) {
                if(out.get(field)!=null && !out.get(field).equals(out.get("product_unit")))throw invalid("Use the product unit for sales and purchases");
                out.put(field,out.get("product_unit"));
            }
        }
        return out;
    }
    @Transactional
    public Map<String,Object> save(String kind,Long id,Map<String,Object> body) {
        Spec s=access(kind);
        if(kind.equals("products") && (body.get("code")==null || body.get("code").toString().isBlank())) {
            body=new LinkedHashMap<>(body);
            body.put("code",id==null?repository.nextProductCode():find(s.table(),id).get("code"));
            if(id==null && (body.get("barcode_symbol")==null || body.get("barcode_symbol").toString().isBlank()))body.put("barcode_symbol",1);
        }
        var values=validate(kind,body);
        if(id==null) {
            id=repository.insert(s,values);
        } else {
            find(s.table(),id);
            if(kind.equals("products")) {
                Object oldUnit=repository.lockProductUnit(id);
                if(!oldUnit.equals(values.get("product_unit")) && repository.hasMovements(id))throw Requests.conflict("A product unit cannot change after inventory movements exist");
            }
            repository.update(s,id,values);
        }
        return show(kind,id);
    }
    @Transactional
    public void delete(String kind,long id) {
        Spec s=access(kind);if(repository.delete(s,id)==0)throw missing();
        images.deleteOwned(kind,id);
    }
    Map<String,Object> resource(String kind,Map<String,Object> row) {
        var a=new LinkedHashMap<>(row);Object id=a.remove("id");
        a.replaceAll((key,value)->value instanceof Timestamp t?t.toInstant().toString():value instanceof java.sql.Date d?d.toString():value);
        var media=images.references(kind,((Number)id).longValue());
        List<String> urls=media.stream().map(x->"/api/media/"+x).toList();
        if(kind.equals("brands") || kind.equals("product-categories")) {
            a.put("image",urls.isEmpty()?"":urls.getFirst());
            a.put("product_count",repository.productCount(kind,id));
        }
        if(kind.equals("products")) {
            a.put("product_category_name",find("product_categories",((Number)a.get("product_category_id")).longValue()).get("name"));
            a.put("brand_name",find("brands",((Number)a.get("brand_id")).longValue()).get("name"));
            a.put("product_unit_name",find("units",((Number)a.get("product_unit")).longValue()));
            for(String f:List.of("sale_unit","purchase_unit"))a.put(f+"_name",a.get(f)==null?"":find("units",((Number)a.get(f)).longValue()));
            a.put("images",Map.of("imageUrls",urls,"id",media.stream().map(Object::toString).toList()));
            String barcode=barcodes.image((String)a.get("code"),((Number)a.get("barcode_symbol")).intValue());
            a.put("barcode_image_url",barcode);a.put("barcode_url",barcode);
            var balances=repository.balances(id);
            BigDecimal total=balances.stream().map(x->(BigDecimal)x.get("quantity")).reduce(BigDecimal.ZERO,BigDecimal::add);
            a.put("in_stock",total);a.put("stock",Map.of("quantity",total));a.put("stocks",balances);a.put("warehouse",balances.stream().map(x->Map.of("id",x.get("warehouse_id"),"name",x.get("name"),"total_quantity",x.get("quantity"))).toList());
        }
        return Map.of("type",kind,"id",id,"attributes",a,"links",Map.of("self","/api/"+kind+"/"+id));
    }
}
