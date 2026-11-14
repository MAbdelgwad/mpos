package com.sendw.mpos.media.api;
import java.util.*;
import com.sendw.mpos.media.application.MediaService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class MediaController {
    private final MediaService media;
    public MediaController(MediaService media){this.media=media;}
    @GetMapping("/media/{id}") public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        var row=media.get(id);
        return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noCache()).contentType(MediaType.parseMediaType((String)row.get("mime"))).body((byte[])row.get("content"));
    }
    @DeleteMapping("/products-image-delete/{id}") public Map<String,Object> delete(@PathVariable UUID id) {
        media.deleteProductImage(id);return Map.of("success",true);
    }
}
