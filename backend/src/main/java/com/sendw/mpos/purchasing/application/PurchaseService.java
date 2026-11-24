package com.sendw.mpos.purchasing.application;
import com.sendw.mpos.identity.application.IdentityService;
import com.sendw.mpos.shared.domain.Requests;
import com.sendw.mpos.inventory.application.StockOperations;
import com.sendw.mpos.purchasing.domain.PurchaseMath;
import com.sendw.mpos.shared.application.RequestDeduplicator;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.identity.application.Permissions;
import java.util.*;
import com.sendw.mpos.payments.application.PaymentBalances;
import com.sendw.mpos.payments.domain.Settlement;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PurchaseService {
    private final PaymentBalances payments;private final PurchaseRepository repository;private final StockOperations stock;private final IdentityService identity;private final RequestDeduplicator idempotency;
    public PurchaseService(PurchaseRepository repository,StockOperations stock,IdentityService identity,RequestDeduplicator idempotency,PaymentBalances payments){this.payments=payments;this.repository=repository;this.stock=stock;this.identity=identity;this.idempotency=idempotency;}
    public Map<String,Object> preview(Map<String,Object> body) { permit(); var lines=Requests.items(body,"purchase_items").stream().map(PurchaseMath::line).toList(); return Map.of("data",PurchaseMath.totals(body,lines)); }
    private void permit(){Permissions.require("manage_purchase");}
    private Map<String,Object> find(long id,boolean lock){return repository.find(id,lock);}
    @Transactional
    public Map<String,Object> save(Long id,Map<String,Object> body,String key){
        permit();if(id==null)return idempotency.execute("purchases",key,body,()->persist(null,body));
        return persist(id,body);
    }
    private Map<String,Object> persist(Long id,Map<String,Object> body){
        if(id!=null){var old=find(id,true);int status=((Number)old.get("status")).intValue();if(status==1||status==4)throw Requests.conflict("Received/cancelled purchases are locked; cancel and create a correction");
            if(!body.containsKey("version") || Validation.integer(body.get("version").toString())!=((Number)old.get("version")).intValue())throw Requests.conflict("Purchase changed; reload before editing");}
        for(String field:List.of("paid_amount","received_amount"))if(Requests.number(body,field,false).signum()!=0)throw Validation.invalid("Record payments through the dedicated payments endpoint");
        long supplier=Requests.id(body,"supplier_id"),warehouse=Requests.id(body,"warehouse_id");int status=Requests.choice(body,"status",2,3);
        var date=Requests.date(body);String notes=Requests.text(body,"notes",2000,false);
        var lines=Requests.items(body,"purchase_items").stream().map(PurchaseMath::line).toList();var seen=new HashSet<Long>();
        for(var line:lines){long product=(long)line.get("product_id");if(!seen.add(product))throw Validation.invalid("Duplicate product in purchase");
            // Hold product/unit rows while receiving so their unit meaning cannot change concurrently.
            var products=repository.lockProduct(product);if(products.isEmpty())throw Validation.invalid("Unknown product");
            line.put("stock_unit_id",products.getFirst().get("product_unit"));
            if(line.get("purchase_unit")!=null && !line.get("purchase_unit").equals(products.getFirst().get("product_unit")))throw Validation.invalid("Use the product unit");
            line.put("purchase_unit",products.getFirst().get("product_unit"));
            var units=repository.lockUnit(line.get("purchase_unit"));
            if(units.isEmpty())throw Validation.invalid("Unknown unit");
            line.put("unit_name_snapshot",units.getFirst().get("name"));
        }
        var totals=PurchaseMath.totals(body,lines);
        var header=new PurchaseRepository.Header(supplier,warehouse,date,status,totals,notes,identity.current().id());
        if(id==null)id=repository.insert(header);else repository.replaceHeader(id,header);
        for(var line:lines)repository.insertLine(id,line);
        if(status==1)post(id,warehouse,"PURCHASE",BigDecimal.ONE);
        return show(id);
    }
    private void post(long id,long warehouse,String kind,BigDecimal sign){
        var units=repository.lockLineUnits(id);
        for(var item:units){
            if(!item.get("stock_unit_id").equals(item.get("product_unit")))throw Requests.conflict("Product unit changed; update the draft before receiving");

        }
        var deltas=repository.quantities(id).stream().map(row->new StockOperations.Delta(warehouse,((Number)row.get("product_id")).longValue(),((BigDecimal)row.get("quantity")).multiply(sign))).toList();
        stock.apply(kind,id,kind+" PUR_"+id,deltas);
    }
    @Transactional
    public Map<String,Object> receive(long id){permit();var p=find(id,true);int status=((Number)p.get("status")).intValue();
        if(status==4)throw Requests.conflict("Cancelled purchase cannot be received");if(status==1)return show(id);
        post(id,((Number)p.get("warehouse_id")).longValue(),"PURCHASE",BigDecimal.ONE);
        repository.setStatus(id,1);return show(id);
    }
    @Transactional
    public Map<String,Object> cancel(long id){permit();var p=find(id,true);int status=((Number)p.get("status")).intValue();if(status==4)return show(id);
        payments.requireUnpaid("purchases",id);
        if(status==1)post(id,((Number)p.get("warehouse_id")).longValue(),"PURCHASE_CANCEL",BigDecimal.ONE.negate());
        repository.setStatus(id,4);return show(id);
    }
    public Map<String,Object> show(long id){permit();return Map.of("data",resource(find(id,false)));}
    private Map<String,Object> resource(Map<String,Object> row){
        var attrs=new LinkedHashMap<>(row);Object id=attrs.remove("id");attrs.replaceAll((k,v)->v instanceof java.sql.Timestamp t?t.toInstant().toString():v instanceof java.sql.Date d?d.toString():v);
        attrs.put("reference_code","PUR_"+id);attrs.put("supplier_name",repository.supplierName(row.get("supplier_id")));attrs.put("warehouse_name",repository.warehouseName(row.get("warehouse_id")));
        var lines=repository.lines(id);attrs.put("purchase_items",lines);
        var balance=Settlement.of((BigDecimal)row.get("grand_total"),payments.paid("purchases",((Number)id).longValue()),((Number)row.get("status")).intValue()==4);
        attrs.put("paid_amount",balance.paid());attrs.put("due_amount",balance.due());attrs.put("payment_status",balance.status());
        return Map.of("id",id,"type","purchases","attributes",attrs);
    }
    public Map<String,Object> list(Map<String,String> q){permit();
        int page=Validation.integer(q.getOrDefault("page[number]","1")),size=Validation.integer(q.getOrDefault("page[size]","20"));if(page<1||size<1||size>100)throw Validation.invalid("Invalid pagination");
        String search=q.getOrDefault("filter[search]","");if(search.length()>255)throw Validation.invalid("Search too long");String pattern="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        Integer status=null;
        if(q.containsKey("status")&&!q.get("status").isBlank()){status=Validation.integer(q.get("status"));if(status<1||status>4)throw Validation.invalid("Invalid status");}
        var result=repository.search(pattern,status,page,size);
        return Map.of("data",result.rows().stream().map(this::resource).toList(),"meta",Map.of("total",result.total(),"current_page",page,"per_page",size));
    }
}
