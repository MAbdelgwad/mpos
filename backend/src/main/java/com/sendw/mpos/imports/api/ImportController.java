package com.sendw.mpos.imports.api;
import com.sendw.mpos.imports.application.ImportService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import tools.jackson.databind.ObjectMapper;
@RestController
@RequestMapping("/api")
public class ImportController {
 private final ImportService service;private final ObjectMapper json;public ImportController(ImportService s,ObjectMapper j){service=s;json=j;}
 @GetMapping("/import-lookups") public Object lookups(){return service.lookups();}
 @GetMapping("/import-partners") public Object partners(){return service.partners();}
 @PostMapping("/import-partners") public Object partner(@RequestBody Map<String,Object>b){return service.savePartner(null,b);}
 @PutMapping("/import-partners/{id}") public Object partner(@PathVariable long id,@RequestBody Map<String,Object>b){return service.savePartner(id,b);}
 @GetMapping("/import-shipments") public Object list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="") String search){return service.list(page,search);}
 @GetMapping("/import-shipments/{id}") public Object show(@PathVariable long id){return service.show(id);}
 @PostMapping("/import-shipments") public Object create(@RequestBody Map<String,Object>b,@RequestHeader("Idempotency-Key") String key){return service.save(null,b,key);}
 @PutMapping("/import-shipments/{id}") public Object edit(@PathVariable long id,@RequestBody Map<String,Object>b){return service.save(id,b,null);}
 @PostMapping("/import-shipments/{id}/receive") public Object receive(@PathVariable long id){return service.receive(id);}
 @PostMapping("/import-shipments/{id}/cancel") public Object cancel(@PathVariable long id){return service.cancel(id);}
 @PostMapping("/import-shipments/{id}/files") public Object upload(@PathVariable long id,@RequestParam String kind,@RequestParam MultipartFile file)throws java.io.IOException{return service.upload(id,kind,file.getOriginalFilename(),file.getBytes());}
 @GetMapping("/import-shipments/{id}/files/{file}") public ResponseEntity<byte[]> download(@PathVariable long id,@PathVariable UUID file){var f=service.download(id,file);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mime())).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(f.filename(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(f.content());}
 @DeleteMapping("/import-shipments/{id}/files/{file}") public Object removeFile(@PathVariable long id,@PathVariable UUID file){return service.removeFile(id,file);}
 @PostMapping("/import-shipments/{id}/expenses") public Object expense(@PathVariable long id,@RequestParam String data,@RequestParam MultipartFile file)throws java.io.IOException{return service.expense(id,json.readValue(data,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){}),file.getOriginalFilename(),file.getBytes());}
 @DeleteMapping("/import-shipments/{id}/expenses/{expense}") public Object removeExpense(@PathVariable long id,@PathVariable long expense){return service.removeExpense(id,expense);}
 @PostMapping("/import-shipments/{id}/sales") public Object sell(@PathVariable long id,@RequestBody Map<String,Object>b,@RequestHeader("Idempotency-Key") String key){return service.sell(id,b,key);}
}
