package com.sendw.mpos.messaging.api;
import com.sendw.mpos.messaging.application.MailService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/mail-settings")
public class MailController {
 private final MailService service;public MailController(MailService s){service=s;}
 @GetMapping public Map<String,Object> get(){return service.settings();}
 @PutMapping public Map<String,Object> save(@RequestBody Map<String,Object>b){return service.save(b);}
 @PostMapping("/test") public Map<String,Object> test(@RequestBody Map<String,String>b){service.test(b.get("email"));return Map.of("success",true);}
}
