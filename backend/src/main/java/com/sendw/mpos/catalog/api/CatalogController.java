package com.sendw.mpos.catalog.api;
import com.sendw.mpos.catalog.application.CatalogService;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/{kind:brands|product-categories|units|customers|suppliers|products}")
public class CatalogController {
    private final CatalogService catalog;
    private final com.sendw.mpos.catalog.application.CatalogWriteService writes;
    public CatalogController(CatalogService catalog,com.sendw.mpos.catalog.application.CatalogWriteService writes) {this.catalog=catalog;this.writes=writes;}
    @GetMapping public Map<String,Object> list(@PathVariable String kind,@RequestParam Map<String,String> params) {return catalog.list(kind,params);}
    @GetMapping("/{id}") public Map<String,Object> show(@PathVariable String kind,@PathVariable long id) {return catalog.show(kind,id);}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@PathVariable String kind,@RequestBody Map<String,Object> body) {return catalog.save(kind,null,body);}
    @RequestMapping(value="/{id}",method={RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.POST},consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> update(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object> body) {return catalog.save(kind,id,body);}
    @RequestMapping(value={"","/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH},consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> multipart(@PathVariable String kind,@PathVariable(required=false) Long id,MultipartHttpServletRequest request) {
        Map<String,Object> body=new LinkedHashMap<>();request.getParameterMap().forEach((k,v)->body.put(k,v[0]));
        var files=request.getMultiFileMap().values().stream().flatMap(List::stream).filter(f->!f.isEmpty()).toList();
        return ResponseEntity.status(id==null?201:200).body(writes.saveWithImages(kind,id,body,com.sendw.mpos.shared.api.Uploads.read(files)));
    }
    @RequestMapping(value={"","/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH},consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> encoded(@PathVariable String kind,@PathVariable(required=false) Long id,@RequestParam Map<String,String> fields) {
        return ResponseEntity.status(id==null?201:200).body(catalog.save(kind,id,new LinkedHashMap<>(fields)));
    }
    @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable String kind,@PathVariable long id) {catalog.delete(kind,id);return Map.of("success",true);}
}
