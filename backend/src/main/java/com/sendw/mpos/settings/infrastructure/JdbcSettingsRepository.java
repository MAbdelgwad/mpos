package com.sendw.mpos.settings.infrastructure;
import com.sendw.mpos.settings.application.SettingsRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcSettingsRepository implements SettingsRepository {
    private final JdbcTemplate db;
    public JdbcSettingsRepository(JdbcTemplate db) { this.db=db; }
    @Override public Map<String,String> settings() { var result=new LinkedHashMap<String,String>();db.queryForList("SELECT key,value FROM ui_settings").forEach(row->result.put((String)row.get("key"),(String)row.get("value")));return result; }
    @Override public void update(String key,String value) { db.update("UPDATE ui_settings SET value=? WHERE key=?",value,key); }
}
