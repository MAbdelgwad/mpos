package com.sendw.mpos.identity.api;
import com.sendw.mpos.identity.application.VerificationService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class VerificationController {
 private final VerificationService service;public VerificationController(VerificationService s){service=s;}
 @PostMapping("/api/public/verify-email") public Map<String,Object> confirm(@RequestBody Map<String,String>b){service.confirm(b.get("token"));return Map.of("success",true);}
 @PostMapping("/api/users/{id}/verification") public Map<String,Object> resend(@PathVariable long id){service.resend(id);return Map.of("success",true);}
}
