package com.sendw.mpos.warehouses.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.sendw.mpos.warehouses.application.WarehouseService;
import com.sendw.mpos.warehouses.application.WarehouseInput;
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final WarehouseService service;
    public WarehouseController(WarehouseService service) { this.service=service; }
@GetMapping
    public Map<String,Object> list(
            @RequestParam(name="page[number]", defaultValue="1") int page,
            @RequestParam(name="page[size]", defaultValue="10") int size,
            @RequestParam(name="filter[search]", defaultValue="") String search,
            @RequestParam(defaultValue="-created_at") String sort) { return service.list(page,size,search,sort); }
@GetMapping("/{id}")
    public Map<String,Object> show(@PathVariable long id) { return service.show(id); }
@PostMapping
    @ResponseStatus(HttpStatus.CREATED)

    public Map<String,Object> create(@Valid @RequestBody WarehouseInput input) { return service.create(input); }
@RequestMapping(value="/{id}",method={RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.POST})

    public Map<String,Object> update(@PathVariable long id, @Valid @RequestBody WarehouseInput input) { return service.update(id,input); }
@DeleteMapping("/{id}")
    public Map<String,Object> delete(@PathVariable long id) { return service.delete(id); }
}
