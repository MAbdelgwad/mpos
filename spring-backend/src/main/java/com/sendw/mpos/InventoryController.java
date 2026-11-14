package com.sendw.mpos;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final JdbcTemplate db;private final StockService stock;private final Idempotency idempotency;
    public InventoryController(JdbcTemplate db,StockService stock,Idempotency idempotency){this.db=db;this.stock=stock;this.idempotency=idempotency;}
    @PostMapping("/{kind:opening|adjustments|transfers}") @Transactional
    public Map<String,Object> move(@PathVariable String kind,@RequestBody Map<String,Object> body,@RequestHeader(value="Idempotency-Key",required=false)String key){
        CatalogService.require(kind.equals("transfers")?"manage_transfers":"manage_adjustments");
        return idempotency.execute("inventory:"+kind,key,body,()->{
            long product=Requests.id(body,"product_id");String reason=Requests.text(body,"reason",1000,true);var deltas=new ArrayList<StockService.Delta>();String type;
            // Freeze the base-unit definition for the duration of a stock change.
            if(db.queryForList("SELECT id FROM products WHERE id=? FOR SHARE",product).isEmpty())throw CatalogService.invalid("Unknown product");
            if(kind.equals("transfers")){long from=Requests.id(body,"from_warehouse_id"),to=Requests.id(body,"to_warehouse_id");if(from==to)throw CatalogService.invalid("Choose different warehouses");
                BigDecimal qty=Requests.number(body,"quantity",true);if(qty.signum()<=0)throw CatalogService.invalid("Quantity must be positive");
                deltas.add(new StockService.Delta(from,product,qty.negate()));deltas.add(new StockService.Delta(to,product,qty));type="TRANSFER";
            }else{long warehouse=Requests.id(body,"warehouse_id");var qty=Requests.number(body,kind.equals("opening")?"quantity":"quantity_delta",true);
                if(qty.signum()==0 || (kind.equals("opening")&&qty.signum()<0))throw CatalogService.invalid("Invalid quantity");deltas.add(new StockService.Delta(warehouse,product,qty));type=kind.equals("opening")?"OPENING":"ADJUSTMENT";}
            UUID operation=stock.apply(type,null,reason,deltas);return Map.of("data",Map.of("operation_id",operation.toString(),"kind",type));
        });
    }
    @GetMapping("/{view:balances|movements}") public Map<String,Object> read(@PathVariable String view,@RequestParam Map<String,String> q){
        CatalogService.require("manage_inventory");int page=CatalogService.integer(q.getOrDefault("page[number]","1")),size=CatalogService.integer(q.getOrDefault("page[size]","50"));if(page<1||size<1||size>100)throw CatalogService.invalid("Invalid pagination");
        String where=" WHERE true";var args=new ArrayList<Object>();for(String f:List.of("warehouse_id","product_id"))if(q.containsKey(f)&&!q.get(f).isBlank()){where+=" AND x."+f+"=?";args.add(CatalogService.positiveId(q.get(f)));}
        boolean ledger=view.equals("movements");String source=ledger?"stock_movements":"stock_balances";
        long count=db.queryForObject("SELECT count(*) FROM "+source+" x"+where,Long.class,args.toArray());args.add(size);args.add(((long)page-1)*size);
        String sql="SELECT x.*,p.name AS product_name,p.code,w.name AS warehouse_name"+(ledger?",o.kind,o.reason,o.purchase_id,o.created_by":"")+" FROM "+source+" x JOIN products p ON p.id=x.product_id JOIN warehouses w ON w.id=x.warehouse_id"+(ledger?" JOIN stock_operations o ON o.id=x.operation_id":"")+where+(ledger?" ORDER BY x.id DESC":" ORDER BY x.warehouse_id,x.product_id")+" LIMIT ? OFFSET ?";
        var rows=db.queryForList(sql,args.toArray());rows.forEach(row->row.replaceAll((k,v)->v instanceof java.sql.Timestamp t?t.toInstant().toString():v));return Map.of("data",rows,"meta",Map.of("total",count,"current_page",page,"per_page",size));
    }
}
