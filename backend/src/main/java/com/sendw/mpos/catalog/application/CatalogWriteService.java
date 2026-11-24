package com.sendw.mpos.catalog.application;
import com.sendw.mpos.media.application.MediaService;
import com.sendw.mpos.media.application.UploadedImage;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CatalogWriteService {
    private final CatalogService catalog;
    private final MediaService media;
    public CatalogWriteService(CatalogService catalog, MediaService media) { this.catalog=catalog; this.media=media; }
    @Transactional
    public Map<String,Object> saveWithImages(String kind, Long id, Map<String,Object> body, List<UploadedImage> files) {
        var result=catalog.save(kind,id,body);
        long saved=((Number)((Map<?,?>)result.get("data")).get("id")).longValue();
        if(!files.isEmpty())media.store(kind,saved,files);
        return catalog.show(kind,saved);
    }
}
