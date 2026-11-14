package com.sendw.mpos.catalog.infrastructure;
import com.sendw.mpos.shared.domain.Validation;
import com.google.zxing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
public final class Barcodes {
    private Barcodes() {}
    public static String image(String code,int symbol) {
        BarcodeFormat format=switch(symbol) {case 1->BarcodeFormat.CODE_128;case 2->BarcodeFormat.CODE_39;case 3->BarcodeFormat.EAN_8;case 4->BarcodeFormat.UPC_A;case 5->BarcodeFormat.EAN_13;default->throw Validation.invalid("Invalid barcode symbol");};
        try {
            var matrix=new MultiFormatWriter().encode(code,format,320,100);
            var image=new BufferedImage(matrix.getWidth(),matrix.getHeight(),BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)image.setRGB(x,y,matrix.get(x,y)?0x000000:0xffffff);
            var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(Exception e){throw Validation.invalid("Product code is not valid for the selected barcode format");}
    }
}
