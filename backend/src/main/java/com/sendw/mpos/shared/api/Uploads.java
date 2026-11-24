package com.sendw.mpos.shared.api;
import com.sendw.mpos.media.application.UploadedImage;
import com.sendw.mpos.shared.domain.Validation;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
public final class Uploads {
    private Uploads() {}
    public static List<UploadedImage> read(List<MultipartFile> files) {
        return files.stream().map(file -> {
            if(file.getSize()>2097152)throw Validation.invalid("Image exceeds 2 MB");
            try { return new UploadedImage(file.getBytes()); }
            catch(IOException e) { throw Validation.invalid("Cannot read uploaded image"); }
        }).toList();
    }
}
