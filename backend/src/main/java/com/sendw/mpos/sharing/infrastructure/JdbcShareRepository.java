package com.sendw.mpos.sharing.infrastructure;
import com.sendw.mpos.sharing.application.ShareRepository;
import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
@org.springframework.stereotype.Repository
public class JdbcShareRepository implements ShareRepository {
 private final JdbcTemplate db;public JdbcShareRepository(JdbcTemplate db){this.db=db;}
 public void create(UUID id,String kind,long document,String hash,String snapshot,Instant expiry,long user){db.update("INSERT INTO invoice_shares(id,kind,document_id,token_hash,snapshot,expires_at,created_by) VALUES(?,?,?,?,?,?,?)",id,kind,document,hash,snapshot,java.sql.Timestamp.from(expiry),user);}
 public List<Map<String,Object>> list(String kind,long document){return db.queryForList("SELECT id,expires_at::text,revoked,created_at::text FROM invoice_shares WHERE kind=? AND document_id=? ORDER BY created_at DESC LIMIT 100",kind,document);}
 public void revoke(String kind,long document,UUID id){db.update("UPDATE invoice_shares SET revoked=true WHERE id=? AND kind=? AND document_id=?",id,kind,document);}
 public Optional<String> snapshot(String hash){var r=db.queryForList("SELECT snapshot FROM invoice_shares WHERE token_hash=? AND revoked=false AND expires_at>now()",String.class,hash);return r.stream().findFirst();}
}
