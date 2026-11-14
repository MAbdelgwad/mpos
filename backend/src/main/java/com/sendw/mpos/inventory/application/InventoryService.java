package com.sendw.mpos.inventory.application;
import com.sendw.mpos.shared.domain.Requests;
import com.sendw.mpos.inventory.application.StockOperations;
import com.sendw.mpos.shared.application.RequestDeduplicator;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.identity.application.Permissions;
import java.util.*;
import java.math.BigDecimal;

import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class InventoryService {
    private final InventoryQueries queries;private final StockOperations stock;private final RequestDeduplicator idempotency;
    public InventoryService(InventoryQueries queries,StockOperations stock,RequestDeduplicator idempotency){this.queries=queries;this.stock=stock;this.idempotency=idempotency;}
     @Transactional
    public Map<String,Object> move( String kind, Map<String,Object> body,String key){
        Permissions.require(kind.equals("transfers")?"manage_transfers":"manage_adjustments");
        return idempotency.execute("inventory:"+kind,key,body,()->{
            long product=Requests.id(body,"product_id");String reason=Requests.text(body,"reason",1000,true);var deltas=new ArrayList<StockOperations.Delta>();String type;
            // Freeze the base-unit definition for the duration of a stock change.
            if(!queries.lockProduct(product))throw Validation.invalid("Unknown product");
            if(kind.equals("transfers")){long from=Requests.id(body,"from_warehouse_id"),to=Requests.id(body,"to_warehouse_id");if(from==to)throw Validation.invalid("Choose different warehouses");
                BigDecimal qty=Requests.number(body,"quantity",true);if(qty.signum()<=0)throw Validation.invalid("Quantity must be positive");
                deltas.add(new StockOperations.Delta(from,product,qty.negate()));deltas.add(new StockOperations.Delta(to,product,qty));type="TRANSFER";
            }else{long warehouse=Requests.id(body,"warehouse_id");var qty=Requests.number(body,kind.equals("opening")?"quantity":"quantity_delta",true);
                if(qty.signum()==0 || (kind.equals("opening")&&qty.signum()<0))throw Validation.invalid("Invalid quantity");deltas.add(new StockOperations.Delta(warehouse,product,qty));type=kind.equals("opening")?"OPENING":"ADJUSTMENT";}
            UUID operation=stock.apply(type,null,reason,deltas);return Map.of("data",Map.of("operation_id",operation.toString(),"kind",type));
        });
    }
     public Map<String,Object> read( String view, Map<String,String> q){
        Permissions.require("manage_inventory");int page=Validation.integer(q.getOrDefault("page[number]","1")),size=Validation.integer(q.getOrDefault("page[size]","50"));if(page<1||size<1||size>100)throw Validation.invalid("Invalid pagination");
        Long warehouse=filter(q,"warehouse_id"),product=filter(q,"product_id");
        var result=queries.search(view.equals("movements"),warehouse,product,page,size);
        return Map.of("data",result.rows(),"meta",Map.of("total",result.total(),"current_page",page,"per_page",size));
    }
    private Long filter(Map<String,String> q,String key) { String value=q.get(key);return value==null||value.isBlank()?null:Validation.positiveId(value); }
}
