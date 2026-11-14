package com.sendw.mpos.media.application;
public record MediaContent(String mime, byte[] content) {
    public MediaContent { content=content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
