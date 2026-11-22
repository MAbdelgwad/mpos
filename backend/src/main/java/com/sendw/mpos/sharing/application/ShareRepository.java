package com.sendw.mpos.sharing.application;
import java.util.*;
import java.time.Instant;
public interface ShareRepository {
 void create(UUID id,String kind,long document,String hash,String snapshot,Instant expiry,long user);
 List<Map<String,Object>> list(String kind,long document);
 void revoke(String kind,long document,UUID id);
 Optional<String> snapshot(String hash);
}
