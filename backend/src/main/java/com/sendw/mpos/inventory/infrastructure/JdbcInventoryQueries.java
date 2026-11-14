package com.sendw.mpos.inventory.infrastructure;
import com.sendw.mpos.inventory.application.InventoryQueries;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcInventoryQueries implements InventoryQueries {
    private final JdbcTemplate db;
    public JdbcInventoryQueries(JdbcTemplate db) { this.db=db; }
    @Override public boolean lockProduct(long id) { return !db.queryForList("SELECT id FROM products WHERE id=? FOR SHARE",id).isEmpty(); }
    @Override public Page search(boolean ledger,Long warehouse,Long product,int page,int size) {
        String where=" WHERE true";var args=new ArrayList<Object>();
        if(warehouse!=null){where+=" AND x.warehouse_id=?";args.add(warehouse);}
        if(product!=null){where+=" AND x.product_id=?";args.add(product);}
        String source=ledger?"stock_movements":"stock_balances";
        long count=db.queryForObject("SELECT count(*) FROM "+source+" x"+where,Long.class,args.toArray());args.add(size);args.add(((long)page-1)*size);
        String sql="SELECT x.*,p.name AS product_name,p.code,w.name AS warehouse_name"+(ledger?",o.kind,o.reason,o.purchase_id,o.sale_id,o.created_by":"")+" FROM "+source+" x JOIN products p ON p.id=x.product_id JOIN warehouses w ON w.id=x.warehouse_id"+(ledger?" JOIN stock_operations o ON o.id=x.operation_id":"")+where+(ledger?" ORDER BY x.id DESC":" ORDER BY x.warehouse_id,x.product_id")+" LIMIT ? OFFSET ?";
        var rows=db.queryForList(sql,args.toArray());rows.forEach(row->row.replaceAll((k,v)->v instanceof java.sql.Timestamp t?t.toInstant().toString():v));
        return new Page(rows,count);
    }
}
