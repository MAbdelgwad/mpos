package com.sendw.mpos.identity.infrastructure;
import com.sendw.mpos.identity.application.AccessRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcAccessRepository implements AccessRepository {
    private final JdbcTemplate db;
    public JdbcAccessRepository(JdbcTemplate db) { this.db=db; }
    @Override public List<Map<String,Object>> permissions() { return db.queryForList("SELECT * FROM permissions ORDER BY id"); }
    @Override public List<Map<String,Object>> role(long id) { return db.queryForList("SELECT * FROM roles WHERE id=?",id); }
    @Override public List<Map<String,Object>> rolePermissions(long id) { return db.queryForList("SELECT p.id,p.name,p.display_name FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id WHERE rp.role_id=? ORDER BY p.id",id); }
    @Override public List<Long> roleIds() { return db.queryForList("SELECT id FROM roles ORDER BY id",Long.class); }
    @Override public boolean permissionExists(long id) { return db.queryForObject("SELECT count(*) FROM permissions WHERE id=?",Integer.class,id)>0; }
    @Override public long createRole(String name) { return db.queryForObject("INSERT INTO roles(name) VALUES (?) RETURNING id",Long.class,name); }
    @Override public int renameRole(long id,String name) { return db.update("UPDATE roles SET name=? WHERE id=?",name,id); }
    @Override public void replacePermissions(long role,List<Long> permissions) { db.update("DELETE FROM role_permissions WHERE role_id=?",role);for(long permission:permissions)db.update("INSERT INTO role_permissions VALUES (?,?)",role,permission); }
    @Override public void deleteRole(long id) { db.update("DELETE FROM roles WHERE id=?",id); }
    @Override public List<Map<String,Object>> user(long id) { return db.queryForList("SELECT id,email,first_name,last_name,language,phone,role,enabled,created_at FROM app_users WHERE id=?",id); }
    @Override public List<Map<String,Object>> roleByName(Object name) { return db.queryForList("SELECT id,name FROM roles WHERE name=?",name); }
    @Override public List<String> roleNames(long id) { return db.queryForList("SELECT name FROM roles WHERE id=?",String.class,id); }
    @Override public UserPage users(String pattern,int page,int size) {
        String where=" WHERE first_name ILIKE ? OR last_name ILIKE ? OR email ILIKE ?";
        long total=db.queryForObject("SELECT count(*) FROM app_users"+where,Long.class,pattern,pattern,pattern);
        var args=new ArrayList<Object>(List.of(pattern,pattern,pattern));String sql="SELECT id FROM app_users"+where+" ORDER BY id";
        if(size>0){sql+=" LIMIT ? OFFSET ?";args.add(size);args.add(((long)page-1)*size);}
        return new UserPage(db.queryForList(sql,Long.class,args.toArray()),total);
    }
    @Override public long createUser(UserFields f,String hash) { return db.queryForObject("INSERT INTO app_users(first_name,last_name,email,phone,password_hash,role,enabled,language) VALUES (?,?,?,?,?,?,?,(SELECT value FROM ui_settings WHERE key='default_language')) RETURNING id",Long.class,f.first(),f.last(),f.email(),f.phone(),hash,f.role(),f.enabled()); }
    @Override public List<String> lockUserRole(long id) { return db.queryForList("SELECT role FROM app_users WHERE id=? FOR UPDATE",String.class,id); }
    @Override public void updateUser(long id,UserFields f) { db.update("UPDATE app_users SET first_name=?,last_name=?,email=?,phone=?,role=?,enabled=? WHERE id=?",f.first(),f.last(),f.email(),f.phone(),f.role(),f.enabled(),id); }
    @Override public void setPassword(long id,String hash) { db.update("UPDATE app_users SET password_hash=? WHERE id=?",hash,id); }
    @Override public void revokeUserTokens(long id) { db.update("DELETE FROM access_tokens WHERE user_id=?",id); }
    @Override public void deleteUser(long id) { db.update("DELETE FROM app_users WHERE id=?",id); }
    @Override public String passwordHash(long id) { return db.queryForObject("SELECT password_hash FROM app_users WHERE id=?",String.class,id); }
}
