package com.sendw.mpos.messaging.infrastructure;
import com.sendw.mpos.messaging.application.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcMailRepository implements MailRepository {
 private final JdbcTemplate db; public JdbcMailRepository(JdbcTemplate db){this.db=db;}
 public MailConfig get(){return db.queryForObject("SELECT * FROM mail_settings WHERE id=1",(r,n)->new MailConfig(r.getString("host"),r.getInt("port"),r.getString("username"),r.getString("secret"),r.getString("security"),r.getString("from_email"),r.getString("from_name"),r.getString("public_url"),r.getBoolean("enabled"),r.getBoolean("verify_new_users")));}
 public void save(MailConfig c){db.update("UPDATE mail_settings SET host=?,port=?,username=?,secret=?,security=?,from_email=?,from_name=?,public_url=?,enabled=?,verify_new_users=? WHERE id=1",c.host(),c.port(),c.username(),c.secret(),c.security(),c.from_email(),c.from_name(),c.public_url(),c.enabled(),c.verify_new_users());}
}
