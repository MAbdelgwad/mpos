package com.sendw.mpos.purchasing.api;
import com.sendw.mpos.purchasing.application.PurchaseService;
import com.sendw.mpos.shared.infrastructure.Idempotency;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseService service;public PurchaseController(PurchaseService service){this.service=service;}
    @PostMapping("/preview") public Map<String,Object> preview(@RequestBody Map<String,Object>b){return service.preview(b);}
    @GetMapping public Map<String,Object> list(@RequestParam Map<String,String> q){return service.list(q);}
    @GetMapping("/{id}") public Map<String,Object> show(@PathVariable long id){return service.show(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@RequestBody Map<String,Object> b,@RequestHeader(value="Idempotency-Key",required=false) String key){return service.save(null,b,key);}
    @PutMapping("/{id}") public Map<String,Object> edit(@PathVariable long id,@RequestBody Map<String,Object>b){return service.save(id,b,null);}
    @PostMapping("/{id}/receive") public Map<String,Object> receive(@PathVariable long id){return service.receive(id);}
    @PostMapping("/{id}/cancel") public Map<String,Object> cancel(@PathVariable long id){return service.cancel(id);}
}
