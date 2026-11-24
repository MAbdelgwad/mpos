package com.sendw.mpos.warehouses.infrastructure;
import com.sendw.mpos.warehouses.application.WarehouseRepository;
import com.sendw.mpos.warehouses.application.WarehouseInput;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcWarehouseRepository implements WarehouseRepository {
    private final JdbcTemplate db;
    public JdbcWarehouseRepository(JdbcTemplate db) { this.db=db; }
    @Override public Page search(String pattern,String sort,boolean descending,int page,int size) {
        if(!Set.of("id","name","phone","country","city","email","zip_code","created_at","updated_at").contains(sort))throw new IllegalArgumentException("Unsupported warehouse sort");
        String where=" WHERE name ILIKE ? OR phone ILIKE ? OR country ILIKE ? OR city ILIKE ? OR email ILIKE ?";
        Object[] params={pattern,pattern,pattern,pattern,pattern};
        long total=db.queryForObject("SELECT count(*) FROM warehouses"+where,Long.class,params);
        String sql="SELECT * FROM warehouses"+where+" ORDER BY "+sort+(descending?" DESC":" ASC")+", id ASC";
        var values=new ArrayList<Object>(Arrays.asList(params));
        if(size>0){sql+=" LIMIT ? OFFSET ?";values.add(size);values.add(((long)page-1)*size);}
        return new Page(db.queryForList(sql,values.toArray()),total);
    }
    @Override public long insert(WarehouseInput input) { return db.queryForObject("INSERT INTO warehouses(name,phone,country,city,email,zip_code) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,input.name(),input.phone(),input.country(),input.city(),input.email(),input.zip_code()); }
    @Override public int update(long id,WarehouseInput input) { return db.update("UPDATE warehouses SET name=?,phone=?,country=?,city=?,email=?,zip_code=?,updated_at=now() WHERE id=?",input.name(),input.phone(),input.country(),input.city(),input.email(),input.zip_code(),id); }
    @Override public int delete(long id) { return db.update("DELETE FROM warehouses WHERE id=?",id); }
    @Override public List<Map<String,Object>> find(long id) { return db.queryForList("SELECT * FROM warehouses WHERE id=?",id); }
    @Override public long count() { return db.queryForObject("SELECT count(*) FROM warehouses",Long.class); }
}
