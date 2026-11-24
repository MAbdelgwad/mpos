package com.sendw.mpos.sharing.api;
import com.sendw.mpos.sharing.application.ShareService;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class ShareController {
 private final ShareService service;public ShareController(ShareService s){service=s;}
 @GetMapping("/api/invoice-shares/{kind}/{id}") public Object list(@PathVariable String kind,@PathVariable long id){return service.list(kind,id);}
 @PostMapping("/api/invoice-shares/{kind}/{id}") public Object create(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,String>b){return service.create(kind,id,Validation.integer(b.getOrDefault("days","7")),b.get("email"));}
 @DeleteMapping("/api/invoice-shares/{kind}/{id}/{share}") public Object revoke(@PathVariable String kind,@PathVariable long id,@PathVariable UUID share){service.revoke(kind,id,share);return Map.of("success",true);}
 @PostMapping("/api/public/invoices/view") public org.springframework.http.ResponseEntity<?> view(@RequestBody Map<String,String>b){return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("Referrer-Policy","no-referrer").body(service.view(b.get("token")));}
}
