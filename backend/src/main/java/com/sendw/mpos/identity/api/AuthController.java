package com.sendw.mpos.identity.api;
import com.sendw.mpos.identity.application.IdentityService;
import com.sendw.mpos.identity.application.LoginResult;

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

    @PostMapping(value="/login", consumes="application/json")
    public LoginResult login(@Valid @RequestBody Login request) { return identity.login(request.email(),request.password()); }
    @PostMapping(value="/login", consumes={"multipart/form-data","application/x-www-form-urlencoded"})
    public LoginResult formLogin(@Valid @ModelAttribute Login request) {return identity.login(request.email(),request.password());}
    @PostMapping("/validate-auth-token")
    public Map<String,Object> token() {return Map.of("success",true);}
    @PostMapping("/logout")
    public Map<String,Object> logout(@RequestHeader("Authorization") String authorization) {
        identity.logout(authorization.substring(7)); return Map.of("success",true,"message","Logout Successfully");
    }
    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status","UP","stage","sales-purchases-and-inventory"); }
}
