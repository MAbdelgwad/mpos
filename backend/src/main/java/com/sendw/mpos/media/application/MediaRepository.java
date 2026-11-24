package com.sendw.mpos.media.application;
import java.util.*;
public interface MediaRepository {
    Optional<MediaContent> find(UUID id);
    int deleteProductImage(UUID id);
    int count(String kind,long owner);
    void deleteOwned(String kind,long owner);
    void save(UUID id,String kind,long owner,String mime,byte[] content);
    List<UUID> references(String kind,long owner);
}
