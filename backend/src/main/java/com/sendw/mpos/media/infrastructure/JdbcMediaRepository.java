package com.sendw.mpos.media.infrastructure;
import com.sendw.mpos.media.application.MediaRepository;
import com.sendw.mpos.media.application.MediaContent;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcMediaRepository implements MediaRepository {
    private final JdbcTemplate db;
    public JdbcMediaRepository(JdbcTemplate db) { this.db=db; }
    @Override public Optional<MediaContent> find(UUID id) { return db.query("SELECT mime,content FROM media WHERE id=?",(rs,row)->new MediaContent(rs.getString("mime"),rs.getBytes("content")),id).stream().findFirst(); }
    @Override public int deleteProductImage(UUID id) { return db.update("DELETE FROM media WHERE id=? AND owner_type='products'",id); }
    @Override public int count(String kind,long owner) { return db.queryForObject("SELECT count(*) FROM media WHERE owner_type=? AND owner_id=?",Integer.class,kind,owner); }
    @Override public void deleteOwned(String kind,long owner) { db.update("DELETE FROM media WHERE owner_type=? AND owner_id=?",kind,owner); }
    @Override public void save(UUID id,String kind,long owner,String mime,byte[] content) { db.update("INSERT INTO media(id,owner_type,owner_id,mime,content) VALUES (?,?,?,?,?)",id,kind,owner,mime,content); }
    @Override public List<UUID> references(String kind,long owner) { return db.queryForList("SELECT id FROM media WHERE owner_type=? AND owner_id=? ORDER BY id",UUID.class,kind,owner); }
}
