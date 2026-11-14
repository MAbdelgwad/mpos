package com.sendw.mpos.identity.infrastructure;
import com.sendw.mpos.identity.application.IdentityRepository;
import com.sendw.mpos.identity.application.IdentityService.Principal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcIdentityRepository implements IdentityRepository {
    private final JdbcTemplate db;
    public JdbcIdentityRepository(JdbcTemplate db) { this.db=db; }
    @Override public void bootstrapAdmin(String email,String passwordHash) {
        db.update("INSERT INTO app_users(email,password_hash,first_name,role) VALUES (?,?,'Administrator','ADMIN') ON CONFLICT (email) DO NOTHING",email,passwordHash);
    }
    @Override public List<Map<String,Object>> enabledUser(String email) { return db.queryForList("SELECT * FROM app_users WHERE email=? AND enabled=true",email); }
    @Override public void removeExpiredTokens() { db.update("DELETE FROM access_tokens WHERE expires_at <= now()"); }
    @Override public void saveToken(String hash,Object userId,Instant expires) { db.update("INSERT INTO access_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",hash,userId,Timestamp.from(expires)); }
    @Override public Optional<Principal> principal(String hash) {
        var rows=db.queryForList("SELECT u.id,u.role FROM access_tokens t JOIN app_users u ON u.id=t.user_id WHERE t.token_hash=? AND t.expires_at>now() AND u.enabled=true",hash);
        return rows.isEmpty()?Optional.empty():Optional.of(new Principal(((Number)rows.getFirst().get("id")).longValue(),(String)rows.getFirst().get("role")));
    }
    @Override public List<String> permissions(String role) { return db.queryForList("SELECT p.name FROM permissions p JOIN role_permissions rp ON p.id=rp.permission_id JOIN roles r ON r.id=rp.role_id WHERE r.name=? ORDER BY p.id",String.class,role); }
    @Override public void changeLanguage(long user,String language) { db.update("UPDATE app_users SET language=? WHERE id=?",language,user); }
    @Override public void revokeToken(String hash) { db.update("DELETE FROM access_tokens WHERE token_hash=?",hash); }
}
