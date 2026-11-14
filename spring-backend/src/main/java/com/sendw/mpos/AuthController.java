package com.sendw.mpos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final IdentityService identity;
    public AuthController(IdentityService identity) { this.identity = identity; }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}

    @PostMapping("/login")
    public Map<String,Object> login(@Valid @RequestBody Login request) { return identity.login(request.email(),request.password()); }
    @PostMapping("/logout")
    public Map<String,Object> logout(@RequestHeader("Authorization") String authorization) {
        identity.logout(authorization.substring(7)); return Map.of("success",true,"message","Logout Successfully");
    }
    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status","UP","stage","identity-and-warehouses"); }
}
