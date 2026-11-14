package com.sendw.mpos;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PurchaseService {
    private final JdbcTemplate db;private final StockService stock;private final IdentityService identity;private final Idempotency idempotency;
    public PurchaseService(JdbcTemplate db,StockService stock,IdentityService identity,Idempotency idempotency){this.db=db;this.stock=stock;this.identity=identity;this.idempotency=idempotency;}
    private void permit(){CatalogService.require("manage_purchase");}
    private Map<String,Object> find(long id,boolean lock){var rows=db.queryForList("SELECT * FROM purchases WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw CatalogService.missing();return rows.getFirst();}
    @Transactional
    public Map<String,Object> save(Long id,Map<String,Object> body,String key){
        permit();if(id==null)return idempotency.execute("purchases",key,body,()->persist(null,body));
        return persist(id,body);
    }
    private Map<String,Object> persist(Long id,Map<String,Object> body){
        if(id!=null){var old=find(id,true);int status=((Number)old.get("status")).intValue();if(status==1||status==4)throw Requests.conflict("Received/cancelled purchases are locked; cancel and create a correction");
            if(!body.containsKey("version") || CatalogService.integer(body.get("version").toString())!=((Number)old.get("version")).intValue())throw Requests.conflict("Purchase changed; reload before editing");}
        for(String field:List.of("paid_amount","received_amount"))if(Requests.number(body,field,false).signum()!=0)throw CatalogService.invalid("Purchase payments are not implemented yet");
        long supplier=Requests.id(body,"supplier_id"),warehouse=Requests.id(body,"warehouse_id");int status=Requests.choice(body,"status",2,3);
        var date=java.sql.Date.valueOf(Requests.date(body));String notes=Requests.text(body,"notes",2000,false);
        var lines=Requests.items(body,"purchase_items").stream().map(PurchaseMath::line).toList();var seen=new HashSet<Long>();
        for(var line:lines){long product=(long)line.get("product_id");if(!seen.add(product))throw CatalogService.invalid("Duplicate product in purchase");
            // Hold product/unit rows while receiving so their unit meaning cannot change concurrently.
            var products=db.queryForList("SELECT product_unit FROM products WHERE id=? FOR SHARE",product);if(products.isEmpty())throw CatalogService.invalid("Unknown product");
            line.put("base_unit",products.getFirst().get("product_unit"));
            if(line.get("purchase_unit")!=null){var units=db.queryForList("SELECT base_unit FROM units WHERE id=? FOR SHARE",line.get("purchase_unit"));if(units.isEmpty() || !units.getFirst().get("base_unit").equals(products.getFirst().get("product_unit")))throw CatalogService.invalid("Purchase unit does not belong to product base unit");}}
        var totals=PurchaseMath.totals(body,lines);
        if(id==null)id=db.queryForObject("INSERT INTO purchases(supplier_id,warehouse_id,date,status,tax_rate,tax_amount,discount,shipping,grand_total,notes,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,supplier,warehouse,date,status,totals.get("tax_rate"),totals.get("tax_amount"),totals.get("discount"),totals.get("shipping"),totals.get("grand_total"),notes,identity.current().id());
        else{db.update("UPDATE purchases SET supplier_id=?,warehouse_id=?,date=?,status=?,tax_rate=?,tax_amount=?,discount=?,shipping=?,grand_total=?,notes=?,version=version+1,updated_at=now() WHERE id=?",supplier,warehouse,date,status,totals.get("tax_rate"),totals.get("tax_amount"),totals.get("discount"),totals.get("shipping"),totals.get("grand_total"),notes,id);db.update("DELETE FROM purchase_items WHERE purchase_id=?",id);}
        for(var line:lines){var args=new ArrayList<Object>();args.add(id);args.addAll(line.values());db.update("INSERT INTO purchase_items(purchase_id,"+String.join(",",line.keySet())+") VALUES ("+String.join(",",Collections.nCopies(args.size(),"?"))+")",args.toArray());}
        if(status==1)post(id,warehouse,"PURCHASE",BigDecimal.ONE);
        return show(id);
    }
    private void post(long id,long warehouse,String kind,BigDecimal sign){
        var units=db.queryForList("SELECT i.product_id,i.purchase_unit,i.base_unit,p.product_unit FROM purchase_items i JOIN products p ON p.id=i.product_id WHERE purchase_id=? ORDER BY i.product_id FOR SHARE OF p",id);
        for(var item:units){
            if(!item.get("base_unit").equals(item.get("product_unit")))throw Requests.conflict("Product base unit changed; update the draft before receiving");
            if(item.get("purchase_unit")==null)continue;
            var rows=db.queryForList("SELECT base_unit FROM units WHERE id=? FOR SHARE",item.get("purchase_unit"));
            if(rows.isEmpty()||!rows.getFirst().get("base_unit").equals(item.get("product_unit")))throw Requests.conflict("Product units changed; update the draft before receiving");
        }
        var deltas=db.queryForList("SELECT product_id,quantity FROM purchase_items WHERE purchase_id=? ORDER BY product_id",id).stream().map(row->new StockService.Delta(warehouse,((Number)row.get("product_id")).longValue(),((BigDecimal)row.get("quantity")).multiply(sign))).toList();
        stock.apply(kind,id,kind+" PUR_"+id,deltas);
    }
    @Transactional
    public Map<String,Object> receive(long id){permit();var p=find(id,true);int status=((Number)p.get("status")).intValue();
        if(status==4)throw Requests.conflict("Cancelled purchase cannot be received");if(status==1)return show(id);
        post(id,((Number)p.get("warehouse_id")).longValue(),"PURCHASE",BigDecimal.ONE);
        db.update("UPDATE purchases SET status=1,version=version+1,updated_at=now() WHERE id=?",id);return show(id);
    }
    @Transactional
    public Map<String,Object> cancel(long id){permit();var p=find(id,true);int status=((Number)p.get("status")).intValue();if(status==4)return show(id);
        if(status==1)post(id,((Number)p.get("warehouse_id")).longValue(),"PURCHASE_CANCEL",BigDecimal.ONE.negate());
        db.update("UPDATE purchases SET status=4,version=version+1,updated_at=now() WHERE id=?",id);return show(id);
    }
    public Map<String,Object> show(long id){permit();return Map.of("data",resource(find(id,false)));}
    private Map<String,Object> resource(Map<String,Object> row){
        var attrs=new LinkedHashMap<>(row);Object id=attrs.remove("id");attrs.replaceAll((k,v)->v instanceof java.sql.Timestamp t?t.toInstant().toString():v instanceof java.sql.Date d?d.toString():v);
        attrs.put("reference_code","PUR_"+id);attrs.put("supplier_name",db.queryForObject("SELECT name FROM suppliers WHERE id=?",String.class,row.get("supplier_id")));attrs.put("warehouse_name",db.queryForObject("SELECT name FROM warehouses WHERE id=?",String.class,row.get("warehouse_id")));
        var lines=db.queryForList("SELECT i.*,p.name,p.code FROM purchase_items i JOIN products p ON p.id=i.product_id WHERE purchase_id=? ORDER BY i.id",id);attrs.put("purchase_items",lines);
        return Map.of("id",id,"type","purchases","attributes",attrs);
    }
    public Map<String,Object> list(Map<String,String> q){permit();
        int page=CatalogService.integer(q.getOrDefault("page[number]","1")),size=CatalogService.integer(q.getOrDefault("page[size]","20"));if(page<1||size<1||size>100)throw CatalogService.invalid("Invalid pagination");
        String search=q.getOrDefault("filter[search]","");if(search.length()>255)throw CatalogService.invalid("Search too long");String pattern="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String where=" WHERE ('PUR_'||p.id::text ILIKE ? OR s.name ILIKE ?)";var args=new ArrayList<Object>(List.of(pattern,pattern));
        if(q.containsKey("status")&&!q.get("status").isBlank()){int status=CatalogService.integer(q.get("status"));if(status<1||status>4)throw CatalogService.invalid("Invalid status");where+=" AND p.status=?";args.add(status);}
        String joins=" FROM purchases p JOIN suppliers s ON s.id=p.supplier_id";long count=db.queryForObject("SELECT count(*)"+joins+where,Long.class,args.toArray());args.add(size);args.add(((long)page-1)*size);
        var rows=db.queryForList("SELECT p.*"+joins+where+" ORDER BY p.id DESC LIMIT ? OFFSET ?",args.toArray());return Map.of("data",rows.stream().map(this::resource).toList(),"meta",Map.of("total",count,"current_page",page,"per_page",size));
    }
}
