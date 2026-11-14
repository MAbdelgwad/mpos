package com.sendw.mpos.media.application;
public record UploadedImage(byte[] content) {
    public UploadedImage { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
    public long getSize() { return content.length; }
    public byte[] getBytes() { return content(); }
}
