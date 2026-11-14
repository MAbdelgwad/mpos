package com.sendw.mpos.catalog.infrastructure;
import com.sendw.mpos.catalog.application.CatalogRepository;
import com.sendw.mpos.catalog.domain.CatalogSchema;
import com.sendw.mpos.catalog.domain.CatalogSchema.Spec;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcCatalogRepository implements CatalogRepository {
    private final JdbcTemplate db;
    public JdbcCatalogRepository(JdbcTemplate db) { this.db=db; }
    private Spec canonical(Spec spec) { return CatalogSchema.table(spec.table()); }
    private void columns(Spec spec,Collection<String> columns) { if(!canonical(spec).fields().containsAll(columns))throw new IllegalArgumentException("Unsupported catalog columns"); }
    @Override public Page search(Spec spec,String pattern,Map<String,Long> filters,String sort,boolean descending,int page,int size) {
        spec=canonical(spec);columns(spec,filters.keySet());
        if(!spec.fields().contains(sort)&&!Set.of("id","created_at","updated_at").contains(sort))throw new IllegalArgumentException("Unsupported sort");
        String where=" WHERE name ILIKE ?";var args=new ArrayList<Object>();args.add(pattern);
        for(var f:filters.entrySet()){where+=" AND "+f.getKey()+"=?";args.add(f.getValue());}
        long total=db.queryForObject("SELECT count(*) FROM "+spec.table()+where,Long.class,args.toArray());
        String sql="SELECT * FROM "+spec.table()+where+" ORDER BY "+sort+(descending?" DESC":" ASC")+",id";
        if(size>0){sql+=" LIMIT ? OFFSET ?";args.add(size);args.add(((long)page-1)*size);}
        return new Page(db.queryForList(sql,args.toArray()),total);
    }
    @Override public List<Map<String,Object>> find(String table,long id) { return db.queryForList("SELECT * FROM "+CatalogSchema.table(table).table()+" WHERE id=?",id); }
    @Override public long insert(Spec spec,Map<String,Object> values) { spec=canonical(spec);columns(spec,values.keySet());return db.queryForObject("INSERT INTO "+spec.table()+"("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+") RETURNING id",Long.class,values.values().toArray()); }
    @Override public void update(Spec spec,long id,Map<String,Object> values) { spec=canonical(spec);columns(spec,values.keySet());var args=new ArrayList<>(values.values());args.add(id);db.update("UPDATE "+spec.table()+" SET "+String.join(",",values.keySet().stream().map(f->f+"=?").toList())+",updated_at=now() WHERE id=?",args.toArray()); }
    @Override public long lockProductUnit(long id) { return db.queryForObject("SELECT product_unit FROM products WHERE id=? FOR UPDATE",Long.class,id); }
    @Override public boolean hasMovements(long product) { return db.queryForObject("SELECT count(*) FROM stock_movements WHERE product_id=?",Long.class,product)>0; }
    @Override public boolean unitUsedWithOtherBase(long unit,Object base) { return db.queryForObject("SELECT count(*) FROM products WHERE (sale_unit=? OR purchase_unit=?) AND product_unit<>?",Long.class,unit,unit,base)>0; }
    @Override public int delete(Spec spec,long id) { return db.update("DELETE FROM "+canonical(spec).table()+" WHERE id=?",id); }
    @Override public long productCount(String kind,Object id) { String foreign=switch(kind){case "brands"->"brand_id";case "product-categories"->"product_category_id";default->throw new IllegalArgumentException("Unsupported product relation");};return db.queryForObject("SELECT count(*) FROM products WHERE "+foreign+"=?",Long.class,id); }
    @Override public List<Map<String,Object>> balances(Object product) { return db.queryForList("SELECT b.warehouse_id,b.product_id,b.quantity,w.name FROM stock_balances b JOIN warehouses w ON w.id=b.warehouse_id WHERE product_id=? ORDER BY warehouse_id",product); }
    @Override public long count(Spec spec) { return db.queryForObject("SELECT count(*) FROM "+canonical(spec).table(),Long.class); }
}
