package com.sendw.mpos.identity.application;
import java.util.*;
public interface VerificationRepository {
 Map<String,Object> lockUser(long id);
 boolean recentlySent(long id);
 void issue(long id,String email,String hash);
 boolean consume(String hash);
}
