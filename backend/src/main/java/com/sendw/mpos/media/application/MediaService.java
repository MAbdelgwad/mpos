package com.sendw.mpos.media.application;
import com.sendw.mpos.shared.domain.Validation;

import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sendw.mpos.media.application.UploadedImage;

@Service
public class MediaService {
    public MediaContent get(UUID id) { return repository.find(id).orElseThrow(Validation::missing); }
    public List<UUID> references(String kind,long id) { return repository.references(kind,id); }
    public void deleteOwned(String kind,long id) { repository.deleteOwned(kind,id); }
    public void deleteProductImage(UUID id) {
        com.sendw.mpos.identity.application.Permissions.require("manage_products");
        if(repository.deleteProductImage(id)==0)throw Validation.missing();
    }
    private final MediaRepository repository;
    public MediaService(MediaRepository repository){this.repository=repository;}
    @Transactional
    public void store(String kind,long id,List<UploadedImage> files) {
        if(!Set.of("products","brands","product-categories","users").contains(kind))throw Validation.invalid("Images not supported for this resource");
        if(files.size()> (kind.equals("products")?5:1))throw Validation.invalid("Too many images");
        int existing=repository.count(kind,id);
        if(kind.equals("products") && files.size()+existing>5)throw Validation.invalid("Maximum five product images");
        if(!kind.equals("products"))repository.deleteOwned(kind,id);
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
                        repository.save(UUID.randomUUID(),kind,id,format.equals("png")?"image/png":"image/jpeg",bytes);
                    } finally {reader.dispose();}
                }
            } catch(IOException e) {throw Validation.invalid("Invalid image");}
        }
    }
}
