package com.sendw.mpos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class IdentityService implements ApplicationRunner {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String dummyHash = encoder.encode(UUID.randomUUID().toString());

    public IdentityService(JdbcTemplate db,
            @Value("${mpos.bootstrap-email}") String email,
            @Value("${mpos.bootstrap-password}") String password) {
        this.db = db; this.bootstrapEmail = email; this.bootstrapPassword = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() && bootstrapPassword.isBlank()) return;
        if (!bootstrapEmail.contains("@") || bootstrapPassword.length() < 12 ||
                bootstrapPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Set a valid MPOS_ADMIN_EMAIL and a password of at least 12 characters and at most 72 UTF-8 bytes");
        }
        db.update("""
            INSERT INTO app_users(email, password_hash, first_name, role)
            VALUES (?, ?, 'Administrator', 'ADMIN') ON CONFLICT (email) DO NOTHING
            """, bootstrapEmail.trim().toLowerCase(Locale.ROOT), encoder.encode(bootstrapPassword));
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        var users = db.queryForList("SELECT * FROM app_users WHERE email = ? AND enabled=true", email.trim().toLowerCase(Locale.ROOT));
        String hash = users.isEmpty() ? dummyHash : (String) users.getFirst().get("password_hash");
        boolean matches = password.getBytes(StandardCharsets.UTF_8).length <= 72 && encoder.matches(password, hash);
        if (!matches || users.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password");
        var user = users.getFirst();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("DELETE FROM access_tokens WHERE expires_at <= now()");
        db.update("INSERT INTO access_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",
                digest(token), user.get("id"), Timestamp.from(Instant.now().plus(2, ChronoUnit.HOURS)));
        var publicUser = new LinkedHashMap<String, Object>();
        for (String field : List.of("id", "email", "first_name", "last_name", "language")) publicUser.put(field, user.get(field));
        publicUser.put("image_url", "");
        List<String> permissions = permissions((String)user.get("role"));
        return Map.of("data", Map.of("token", token, "user", publicUser, "permissions", permissions),
                "message", "Logged in successfully.");
    }

    public Optional<Principal> authenticate(String token) {
        var users = db.queryForList("""
            SELECT u.id, u.role FROM access_tokens t JOIN app_users u ON u.id=t.user_id
            WHERE t.token_hash=? AND t.expires_at > now() AND u.enabled=true
            """, digest(token));
        return users.isEmpty() ? Optional.empty() : Optional.of(new Principal(
                ((Number) users.getFirst().get("id")).longValue(), (String) users.getFirst().get("role")));
    }

    public List<String> permissions(String role) {
        return db.queryForList("SELECT p.name FROM permissions p JOIN role_permissions rp ON p.id=rp.permission_id JOIN roles r ON r.id=rp.role_id WHERE r.name=? ORDER BY p.id",String.class,role);
    }
    public Principal current() {
        return (Principal) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    public void logout(String token) { db.update("DELETE FROM access_tokens WHERE token_hash=?", digest(token)); }

    public static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record Principal(long id, String role) {}
}
