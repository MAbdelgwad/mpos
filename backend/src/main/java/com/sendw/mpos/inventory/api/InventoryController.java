package com.sendw.mpos.inventory.api;
import com.sendw.mpos.shared.infrastructure.Idempotency;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.sendw.mpos.inventory.application.InventoryService;
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service=service; }
@PostMapping("/{kind:opening|adjustments|transfers}")
    public Map<String,Object> move(@PathVariable String kind,@RequestBody Map<String,Object> body,@RequestHeader(value="Idempotency-Key",required=false)String key) { return service.move(kind,body,key); }
@GetMapping("/{view:balances|movements}") public Map<String,Object> read(@PathVariable String view,@RequestParam Map<String,String> q) { return service.read(view,q); }
}
