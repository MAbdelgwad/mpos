package com.sendw.mpos.identity.api;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.http.*;

import com.sendw.mpos.identity.application.AccessService;
@RestController
@RequestMapping("/api")
public class AccessController {
    private final AccessService service;
    public AccessController(AccessService service) { this.service=service; }
@GetMapping("/permissions") public Map<String,Object> permissions() { return service.permissions(); }
@GetMapping("/roles") public Map<String,Object> roles() { return service.roles(); }
@GetMapping("/roles/{id}") public Map<String,Object> getRole(@PathVariable long id) { return service.getRole(id); }
@RequestMapping(value={"/roles","/roles/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH})

    public ResponseEntity<?> saveRole(@PathVariable(required=false) Long id,@RequestBody Map<String,Object> body) { return ResponseEntity.status(id==null?201:200).body(service.saveRole(id,body)); }
@DeleteMapping("/roles/{id}") public Map<String,Object> deleteRole(@PathVariable long id) { return service.deleteRole(id); }
@GetMapping("/users") public Map<String,Object> users(@RequestParam Map<String,String> q) { return service.users(q); }
@GetMapping("/users/{id}") public Map<String,Object> getUser(@PathVariable long id) { return service.getUser(id); }
@RequestMapping(value={"/users","/users/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH},consumes="application/json")
     public ResponseEntity<?> jsonUser(@PathVariable(required=false) Long id,@RequestBody Map<String,Object> body) { return ResponseEntity.status(id==null?201:200).body(service.jsonUser(id,body)); }
@RequestMapping(value={"/users","/users/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH},consumes="multipart/form-data")
     public ResponseEntity<?> formUser(@PathVariable(required=false) Long id,MultipartHttpServletRequest req) { var body=new LinkedHashMap<String,Object>();req.getParameterMap().forEach((k,v)->body.put(k,v[0]));var files=req.getMultiFileMap().values().stream().flatMap(List::stream).filter(f->!f.isEmpty()).toList();return ResponseEntity.status(id==null?201:200).body(service.formUser(id,body,com.sendw.mpos.shared.api.Uploads.read(files))); }
@DeleteMapping("/users/{id}")  public Map<String,Object> deleteUser(@PathVariable long id) { return service.deleteUser(id); }
@PatchMapping("/change-password")  public Map<String,Object> changePassword(@RequestBody com.sendw.mpos.identity.application.PasswordChange b) { return service.changePassword(b); }
}
