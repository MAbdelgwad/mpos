package com.sendw.mpos.sales.infrastructure;

import com.sendw.mpos.sales.application.SaleRepository;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSaleRepository implements SaleRepository {
    private final JdbcTemplate db;
    public JdbcSaleRepository(JdbcTemplate db) { this.db=db; }
    @Override public Map<String,Object> find(long id, boolean lock) {
        var rows=db.queryForList("SELECT * FROM sales WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw Validation.missing();return rows.getFirst();
    }
    @Override public List<Map<String,Object>> lockProduct(long id) { return db.queryForList("SELECT product_unit FROM products WHERE id=? FOR SHARE",id); }
    @Override public List<Map<String,Object>> lockUnit(Object id) { return db.queryForList("SELECT id,name FROM units WHERE id=? FOR SHARE",id); }
    private List<Object> values(Header h) {
        var t=h.totals();
        return new ArrayList<>(Arrays.asList(h.customer(),h.warehouse(),java.sql.Date.valueOf(h.date()),h.status(),t.get("tax_rate"),t.get("tax_amount"),t.get("discount"),t.get("shipping"),t.get("grand_total"),h.notes()));
    }
    @Override public long insert(Header header) {
        var args=values(header);args.add(header.actor());
        return db.queryForObject("INSERT INTO sales(customer_id,warehouse_id,date,status,tax_rate,tax_amount,discount,shipping,grand_total,notes,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,args.toArray());
    }
    @Override public void replaceHeader(long id, Header header) {
        var args=values(header);args.add(id);
        db.update("UPDATE sales SET customer_id=?,warehouse_id=?,date=?,status=?,tax_rate=?,tax_amount=?,discount=?,shipping=?,grand_total=?,notes=?,version=version+1,updated_at=now() WHERE id=?",args.toArray());
        db.update("DELETE FROM sale_items WHERE sale_id=?",id);
    }
    @Override public void insertLine(long sale, Map<String,Object> line) {
        var columns=List.of("product_id","sale_unit","product_price","quantity","discount_type","discount_value","tax_type","tax_value","net_unit_price","discount_amount","tax_amount","sub_total","stock_unit_id","unit_name_snapshot");
        var args=new ArrayList<Object>();args.add(sale);columns.forEach(column->args.add(line.get(column)));
        db.update("INSERT INTO sale_items(sale_id,"+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(args.size(),"?"))+")",args.toArray());
    }
    @Override public List<Map<String,Object>> lockLineUnits(long sale) {
        return db.queryForList("SELECT i.product_id,i.sale_unit,i.stock_unit_id,p.product_unit FROM sale_items i JOIN products p ON p.id=i.product_id WHERE sale_id=? ORDER BY i.product_id FOR SHARE OF p",sale);
    }
    @Override public List<Map<String,Object>> quantities(long sale) { return db.queryForList("SELECT product_id,quantity FROM sale_items WHERE sale_id=? ORDER BY product_id",sale); }
    @Override public void setStatus(long id,int status) { db.update("UPDATE sales SET status=?,version=version+1,updated_at=now() WHERE id=?",status,id); }
    @Override public String customerName(Object id) { return db.queryForObject("SELECT name FROM customers WHERE id=?",String.class,id); }
    @Override public String warehouseName(Object id) { return db.queryForObject("SELECT name FROM warehouses WHERE id=?",String.class,id); }
    @Override public List<Map<String,Object>> lines(Object sale) { return db.queryForList("SELECT i.*,p.name,p.code,i.unit_name_snapshot AS unit_name FROM sale_items i JOIN products p ON p.id=i.product_id WHERE sale_id=? ORDER BY i.id",sale); }
    @Override public Page search(String pattern,Integer status,int page,int size) {
        String where=" WHERE ('SAL_'||p.id::text ILIKE ? OR s.name ILIKE ?)";
        var args=new ArrayList<Object>(List.of(pattern,pattern));
        if(status!=null){where+=" AND p.status=?";args.add(status);}
        String joins=" FROM sales p JOIN customers s ON s.id=p.customer_id";
        long count=db.queryForObject("SELECT count(*)"+joins+where,Long.class,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        return new Page(db.queryForList("SELECT p.*"+joins+where+" ORDER BY p.id DESC LIMIT ? OFFSET ?",args.toArray()),count);
    }
}
