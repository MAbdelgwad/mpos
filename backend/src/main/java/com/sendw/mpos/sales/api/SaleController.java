package com.sendw.mpos.sales.api;
import com.sendw.mpos.sales.application.SaleService;
import java.util.*;
import com.sendw.mpos.sales.application.SaleRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private final SaleService service;public SaleController(SaleService service){this.service=service;}
    @PostMapping("/preview") public Map<String,Object> preview(@RequestBody SaleRequest b){return service.preview(b.values());}
    @GetMapping public Map<String,Object> list(@RequestParam Map<String,String> q){return service.list(q);}
    @GetMapping("/{id}") public Map<String,Object> show(@PathVariable long id){return service.show(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@RequestBody SaleRequest b,@RequestHeader(value="Idempotency-Key",required=false) String key){return service.save(null,b.values(),key);}
    @PutMapping("/{id}") public Map<String,Object> edit(@PathVariable long id,@RequestBody SaleRequest b){return service.save(id,b.values(),null);}
    @PostMapping("/{id}/complete") public Map<String,Object> complete(@PathVariable long id){return service.complete(id);}
    @PostMapping("/{id}/cancel") public Map<String,Object> cancel(@PathVariable long id){return service.cancel(id);}
}
