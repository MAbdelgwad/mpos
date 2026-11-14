package com.sendw.mpos;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class StockService {
    public record Delta(long warehouse,long product,BigDecimal quantity) {}
    private record Key(long warehouse,long product) implements Comparable<Key>{public int compareTo(Key other){int n=Long.compare(warehouse,other.warehouse);return n==0?Long.compare(product,other.product):n;}}
    private final JdbcTemplate db;private final IdentityService identity;
    public StockService(JdbcTemplate db,IdentityService identity){this.db=db;this.identity=identity;}
    // All callers hold a database transaction. Lock keys in stable order for transfers and multi-line receipts.
    public UUID apply(String kind,Long purchase,String reason,List<Delta> deltas){
        var totals=new TreeMap<Key,BigDecimal>();for(var d:deltas)totals.merge(new Key(d.warehouse(),d.product()),d.quantity(),BigDecimal::add);
        var balances=new TreeMap<Key,BigDecimal>();
        for(var key:totals.keySet()){
            db.update("INSERT INTO stock_balances(warehouse_id,product_id) VALUES (?,?) ON CONFLICT DO NOTHING",key.warehouse(),key.product());
            BigDecimal quantity=db.queryForObject("SELECT quantity FROM stock_balances WHERE warehouse_id=? AND product_id=? FOR UPDATE",BigDecimal.class,key.warehouse(),key.product());
            if(kind.equals("OPENING") && db.queryForObject("SELECT count(*) FROM stock_movements WHERE warehouse_id=? AND product_id=?",Long.class,key.warehouse(),key.product())>0)throw Requests.conflict("Opening balance is only allowed before the first movement");
            BigDecimal next=quantity.add(totals.get(key));if(next.signum()<0)throw Requests.conflict("Insufficient stock for product "+key.product()+" in warehouse "+key.warehouse());
            balances.put(key,next);
        }
        UUID operation=UUID.randomUUID();db.update("INSERT INTO stock_operations(id,kind,purchase_id,reason,created_by) VALUES (?,?,?,?,?)",operation,kind,purchase,reason,identity.current().id());
        for(var entry:totals.entrySet()){
            if(entry.getValue().signum()==0)throw CatalogService.invalid("Zero stock movement");var key=entry.getKey();
            db.update("UPDATE stock_balances SET quantity=? WHERE warehouse_id=? AND product_id=?",balances.get(key),key.warehouse(),key.product());
            db.update("INSERT INTO stock_movements(operation_id,warehouse_id,product_id,quantity_delta,quantity_after) VALUES (?,?,?,?,?)",operation,key.warehouse(),key.product(),entry.getValue(),balances.get(key));
        }
        return operation;
    }
}
