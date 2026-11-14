package com.sendw.mpos.media.application;
import com.sendw.mpos.shared.domain.Validation;

import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sendw.mpos.media.application.UploadedImage;

@Service
public class MediaService {
    public Map<String,Object> get(UUID id) {
        var rows=db.queryForList("SELECT mime,content FROM media WHERE id=?",id);
        if(rows.isEmpty())throw Validation.missing();return rows.getFirst();
    }
    public void deleteProductImage(UUID id) {
        com.sendw.mpos.identity.application.Permissions.require("manage_products");
        if(db.update("DELETE FROM media WHERE id=? AND owner_type='products'",id)==0)throw Validation.missing();
    }
    private final JdbcTemplate db;
    public MediaService(JdbcTemplate db){this.db=db;}
    @Transactional
    public void store(String kind,long id,List<UploadedImage> files) {
        if(!Set.of("products","brands","product-categories","users").contains(kind))throw Validation.invalid("Images not supported for this resource");
        if(files.size()> (kind.equals("products")?5:1))throw Validation.invalid("Too many images");
        int existing=db.queryForObject("SELECT count(*) FROM media WHERE owner_type=? AND owner_id=?",Integer.class,kind,id);
        if(kind.equals("products") && files.size()+existing>5)throw Validation.invalid("Maximum five product images");
        if(!kind.equals("products"))db.update("DELETE FROM media WHERE owner_type=? AND owner_id=?",kind,id);
        for(var file:files) {
            if(file.getSize()>2097152)throw Validation.invalid("Image exceeds 2 MB");
            try {
                byte[] bytes=file.getBytes();
                try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw Validation.invalid("Invalid image");
                    var reader=readers.next();
                    try {
                        reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                        if(!Set.of("png","jpeg","jpg").contains(format) || (long)reader.getWidth(0)*reader.getHeight(0)>20000000)throw Validation.invalid("Only JPEG/PNG up to 20 megapixels are supported");
                        reader.read(0); // Reject malformed/truncated data before persisting it.
                        db.update("INSERT INTO media(id,owner_type,owner_id,mime,content) VALUES (?,?,?,?,?)",UUID.randomUUID(),kind,id,format.equals("png")?"image/png":"image/jpeg",bytes);
                    } finally {reader.dispose();}
                }
            } catch(IOException e) {throw Validation.invalid("Invalid image");}
        }
    }
}
