package com.sendw.mpos;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class MediaController {
    private final JdbcTemplate db;
    public MediaController(JdbcTemplate db){this.db=db;}
    @GetMapping("/media/{id}") public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        var rows=db.queryForList("SELECT mime,content FROM media WHERE id=?",id);if(rows.isEmpty())throw CatalogService.missing();var row=rows.getFirst();
        return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noCache()).contentType(MediaType.parseMediaType((String)row.get("mime"))).body((byte[])row.get("content"));
    }
    @DeleteMapping("/products-image-delete/{id}") public Map<String,Object> delete(@PathVariable UUID id) {
        CatalogService.require("manage_products");
        if(db.update("DELETE FROM media WHERE id=? AND owner_type='products'",id)==0)throw CatalogService.missing();return Map.of("success",true);
    }
}
