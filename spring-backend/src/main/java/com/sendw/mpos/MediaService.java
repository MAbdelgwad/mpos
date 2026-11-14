package com.sendw.mpos;

import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private final JdbcTemplate db;
    public MediaService(JdbcTemplate db){this.db=db;}
    @Transactional
    public void store(String kind,long id,List<MultipartFile> files) {
        if(!Set.of("products","brands","product-categories","users").contains(kind))throw CatalogService.invalid("Images not supported for this resource");
        if(files.size()> (kind.equals("products")?5:1))throw CatalogService.invalid("Too many images");
        int existing=db.queryForObject("SELECT count(*) FROM media WHERE owner_type=? AND owner_id=?",Integer.class,kind,id);
        if(kind.equals("products") && files.size()+existing>5)throw CatalogService.invalid("Maximum five product images");
        if(!kind.equals("products"))db.update("DELETE FROM media WHERE owner_type=? AND owner_id=?",kind,id);
        for(var file:files) {
            if(file.getSize()>2097152)throw CatalogService.invalid("Image exceeds 2 MB");
            try {
                byte[] bytes=file.getBytes();
                try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw CatalogService.invalid("Invalid image");
                    var reader=readers.next();
                    try {
                        reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                        if(!Set.of("png","jpeg","jpg").contains(format) || (long)reader.getWidth(0)*reader.getHeight(0)>20000000)throw CatalogService.invalid("Only JPEG/PNG up to 20 megapixels are supported");
                        reader.read(0); // Reject malformed/truncated data before persisting it.
                        db.update("INSERT INTO media(id,owner_type,owner_id,mime,content) VALUES (?,?,?,?,?)",UUID.randomUUID(),kind,id,format.equals("png")?"image/png":"image/jpeg",bytes);
                    } finally {reader.dispose();}
                }
            } catch(IOException e) {throw CatalogService.invalid("Invalid image");}
        }
    }
}
