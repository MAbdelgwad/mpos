package com.sendw.mpos.purchasing.infrastructure;

import com.sendw.mpos.purchasing.application.PurchaseRepository;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPurchaseRepository implements PurchaseRepository {
    private final JdbcTemplate db;
    public JdbcPurchaseRepository(JdbcTemplate db) { this.db=db; }
    @Override public Map<String,Object> find(long id, boolean lock) {
        var rows=db.queryForList("SELECT * FROM purchases WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw Validation.missing();return rows.getFirst();
    }
    @Override public List<Map<String,Object>> lockProduct(long id) { return db.queryForList("SELECT product_unit FROM products WHERE id=? FOR SHARE",id); }
    @Override public List<Map<String,Object>> lockUnit(Object id) { return db.queryForList("SELECT base_unit FROM units WHERE id=? FOR SHARE",id); }
    private List<Object> values(Header h) {
        var t=h.totals();
        return new ArrayList<>(Arrays.asList(h.supplier(),h.warehouse(),h.date(),h.status(),t.get("tax_rate"),t.get("tax_amount"),t.get("discount"),t.get("shipping"),t.get("grand_total"),h.notes()));
    }
    @Override public long insert(Header header) {
        var args=values(header);args.add(header.actor());
        return db.queryForObject("INSERT INTO purchases(supplier_id,warehouse_id,date,status,tax_rate,tax_amount,discount,shipping,grand_total,notes,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,args.toArray());
    }
    @Override public void replaceHeader(long id, Header header) {
        var args=values(header);args.add(id);
        db.update("UPDATE purchases SET supplier_id=?,warehouse_id=?,date=?,status=?,tax_rate=?,tax_amount=?,discount=?,shipping=?,grand_total=?,notes=?,version=version+1,updated_at=now() WHERE id=?",args.toArray());
        db.update("DELETE FROM purchase_items WHERE purchase_id=?",id);
    }
    @Override public void insertLine(long purchase, Map<String,Object> line) {
        var columns=List.of("product_id","purchase_unit","product_cost","quantity","discount_type","discount_value","tax_type","tax_value","net_unit_cost","discount_amount","tax_amount","sub_total","base_unit");
        var args=new ArrayList<Object>();args.add(purchase);columns.forEach(column->args.add(line.get(column)));
        db.update("INSERT INTO purchase_items(purchase_id,"+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(args.size(),"?"))+")",args.toArray());
    }
    @Override public List<Map<String,Object>> lockLineUnits(long purchase) {
        return db.queryForList("SELECT i.product_id,i.purchase_unit,i.base_unit,p.product_unit FROM purchase_items i JOIN products p ON p.id=i.product_id WHERE purchase_id=? ORDER BY i.product_id FOR SHARE OF p",purchase);
    }
    @Override public List<Map<String,Object>> quantities(long purchase) { return db.queryForList("SELECT product_id,quantity FROM purchase_items WHERE purchase_id=? ORDER BY product_id",purchase); }
    @Override public void setStatus(long id,int status) { db.update("UPDATE purchases SET status=?,version=version+1,updated_at=now() WHERE id=?",status,id); }
    @Override public String supplierName(Object id) { return db.queryForObject("SELECT name FROM suppliers WHERE id=?",String.class,id); }
    @Override public String warehouseName(Object id) { return db.queryForObject("SELECT name FROM warehouses WHERE id=?",String.class,id); }
    @Override public List<Map<String,Object>> lines(Object purchase) { return db.queryForList("SELECT i.*,p.name,p.code FROM purchase_items i JOIN products p ON p.id=i.product_id WHERE purchase_id=? ORDER BY i.id",purchase); }
    @Override public Page search(String pattern,Integer status,int page,int size) {
        String where=" WHERE ('PUR_'||p.id::text ILIKE ? OR s.name ILIKE ?)";
        var args=new ArrayList<Object>(List.of(pattern,pattern));
        if(status!=null){where+=" AND p.status=?";args.add(status);}
        String joins=" FROM purchases p JOIN suppliers s ON s.id=p.supplier_id";
        long count=db.queryForObject("SELECT count(*)"+joins+where,Long.class,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        return new Page(db.queryForList("SELECT p.*"+joins+where+" ORDER BY p.id DESC LIMIT ? OFFSET ?",args.toArray()),count);
    }
}
