package com.sendw.mpos.identity.infrastructure;
import com.sendw.mpos.identity.application.VerificationRepository;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
@org.springframework.stereotype.Repository
public class JdbcVerificationRepository implements VerificationRepository {
 private final JdbcTemplate db;public JdbcVerificationRepository(JdbcTemplate db){this.db=db;}
 public Map<String,Object> lockUser(long id){var r=db.queryForList("SELECT email,email_verified FROM app_users WHERE id=? FOR UPDATE",id);if(r.isEmpty())throw Validation.missing();return r.getFirst();}
 public boolean recentlySent(long id){return db.queryForObject("SELECT count(*) FROM email_verifications WHERE user_id=? AND sent_at>now()-interval '1 minute'",Integer.class,id)>0;}
 public void issue(long id,String email,String hash){db.update("UPDATE app_users SET email_verified=false WHERE id=?",id);db.update("DELETE FROM access_tokens WHERE user_id=?",id);db.update("INSERT INTO email_verifications(user_id,email,token_hash,expires_at) VALUES(?,?,?,now()+interval '24 hours') ON CONFLICT(user_id) DO UPDATE SET email=EXCLUDED.email,token_hash=EXCLUDED.token_hash,expires_at=EXCLUDED.expires_at,sent_at=now()",id,email,hash);}
 public boolean consume(String hash){var rows=db.queryForList("DELETE FROM email_verifications WHERE token_hash=? AND expires_at>now() RETURNING user_id,email",hash);if(rows.isEmpty())return false;var r=rows.getFirst();return db.update("UPDATE app_users SET email_verified=true WHERE id=? AND email=?",r.get("user_id"),r.get("email"))==1;}
}
