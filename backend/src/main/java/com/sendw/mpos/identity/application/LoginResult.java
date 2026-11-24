package com.sendw.mpos.identity.application;
import java.util.List;
/** Explicit public contract: never exposes credential hashes or enabled/role internals. */
public record LoginResult(Session data,String message) {
    public record Session(String token,User user,List<String> permissions) {
        public Session { permissions=List.copyOf(permissions); }
    }
    public record User(long id,String email,String first_name,String last_name,String language,String image_url) {}
}
