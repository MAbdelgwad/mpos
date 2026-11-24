package com.sendw.mpos.identity.application;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
public final class Permissions {
private Permissions() {}
    public static void require(String permission) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(permission)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Forbidden");
    }
}
