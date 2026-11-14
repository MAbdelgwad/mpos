package com.sendw.mpos.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class IdentityService implements ApplicationRunner {
    private final IdentityRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String dummyHash = encoder.encode(UUID.randomUUID().toString());

    public IdentityService(IdentityRepository repository,
            @Value("${mpos.bootstrap-email}") String email,
            @Value("${mpos.bootstrap-password}") String password) {
        this.repository = repository; this.bootstrapEmail = email; this.bootstrapPassword = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() && bootstrapPassword.isBlank()) return;
        if (!bootstrapEmail.contains("@") || bootstrapPassword.length() < 12 ||
                bootstrapPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Set a valid MPOS_ADMIN_EMAIL and a password of at least 12 characters and at most 72 UTF-8 bytes");
        }
        repository.bootstrapAdmin(bootstrapEmail.trim().toLowerCase(Locale.ROOT), encoder.encode(bootstrapPassword));
    }

    @Transactional
    public LoginResult login(String email, String password) {
        var users = repository.enabledUser(email.trim().toLowerCase(Locale.ROOT));
        String hash = users.isEmpty() ? dummyHash : (String) users.getFirst().get("password_hash");
        boolean matches = password.getBytes(StandardCharsets.UTF_8).length <= 72 && encoder.matches(password, hash);
        if (!matches || users.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password");
        var user = users.getFirst();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.removeExpiredTokens();
        repository.saveToken(digest(token),user.get("id"),Instant.now().plus(2,ChronoUnit.HOURS));
        var publicUser=new LoginResult.User(((Number)user.get("id")).longValue(),(String)user.get("email"),
                (String)user.get("first_name"),(String)user.get("last_name"),(String)user.get("language"),"");
        return new LoginResult(new LoginResult.Session(token,publicUser,permissions((String)user.get("role"))),"Logged in successfully.");
    }

    public Optional<Principal> authenticate(String token) {
        return repository.principal(digest(token));
    }

    public List<String> permissions(String role) {
        return repository.permissions(role);
    }
    public Principal current() {
        return (Principal) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    public void changeLanguage(String language) { repository.changeLanguage(current().id(),language); }
    public void logout(String token) { repository.revokeToken(digest(token)); }

    public static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record Principal(long id, String role) {}
}
