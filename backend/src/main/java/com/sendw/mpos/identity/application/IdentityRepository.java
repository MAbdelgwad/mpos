package com.sendw.mpos.identity.application;
import java.time.Instant;
import java.util.*;
public interface IdentityRepository {
    void bootstrapAdmin(String email, String passwordHash);
    List<Map<String,Object>> enabledUser(String email);
    void removeExpiredTokens();
    void saveToken(String hash, Object userId, Instant expires);
    Optional<IdentityService.Principal> principal(String hash);
    List<String> permissions(String role);
    void changeLanguage(long user,String language);
    void revokeToken(String hash);
}
