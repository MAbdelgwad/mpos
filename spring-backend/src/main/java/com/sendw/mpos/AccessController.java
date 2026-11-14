package com.sendw.mpos;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.http.*;

@RestController
@RequestMapping("/api")
public class AccessController {
    private final JdbcTemplate db;private final IdentityService identity;private final MediaService media;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    public AccessController(JdbcTemplate db,IdentityService identity,MediaService media){this.db=db;this.identity=identity;this.media=media;}
    void admin(){CatalogService.require("manage_users");}
    static String text(Map<String,Object> b,String key,int max,boolean required){
        String value=Objects.toString(b.get(key),"").trim();if((required && value.isEmpty()) || value.length()>max)throw CatalogService.invalid("Invalid "+key);return value;
    }
    static Map<String,Object> wrap(String type,Object id,Map<String,Object> a){return Map.of("id",id,"type",type,"attributes",a);}
    @GetMapping("/permissions") public Map<String,Object> permissions(){
        var data=db.queryForList("SELECT * FROM permissions ORDER BY id").stream().map(p->wrap("permissions",p.get("id"),Map.of("name",p.get("name"),"display_name",p.get("display_name")))).toList();
        return Map.of("data",data);
    }
    Map<String,Object> role(long id){
        var rows=db.queryForList("SELECT * FROM roles WHERE id=?",id);if(rows.isEmpty())throw CatalogService.missing();var row=rows.getFirst();
        return wrap("roles",id,Map.of("name",row.get("name"),"display_name",row.get("name"),"is_default",row.get("protected_role"),"created_at",row.get("created_at").toString(),"permissions",db.queryForList("SELECT p.id,p.name,p.display_name FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id WHERE rp.role_id=? ORDER BY p.id",id)));
    }
    @GetMapping("/roles") public Map<String,Object> roles(){CatalogService.require("manage_roles");var data=db.queryForList("SELECT id FROM roles ORDER BY id",Long.class).stream().map(this::role).toList();return Map.of("data",data,"meta",Map.of("total",data.size()));}
    @GetMapping("/roles/{id}") public Map<String,Object> getRole(@PathVariable long id){CatalogService.require("manage_roles");return Map.of("data",role(id));}
    @RequestMapping(value={"/roles","/roles/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH})
    @Transactional
    public ResponseEntity<?> saveRole(@PathVariable(required=false) Long id,@RequestBody Map<String,Object> body){
        CatalogService.require("manage_roles");boolean create=id==null;String name=text(body,"name",50,true);
        if(id!=null)role(id);
        if(id!=null && Boolean.TRUE.equals(db.queryForObject("SELECT protected_role FROM roles WHERE id=?",Boolean.class,id)))throw CatalogService.invalid("Built-in roles cannot be changed");
        if(!(body.get("permissions") instanceof List<?> raw))throw CatalogService.invalid("permissions must be an array");
        var permissions=raw.stream().map(CatalogService::positiveId).distinct().toList();
        for(long permission:permissions)if(db.queryForObject("SELECT count(*) FROM permissions WHERE id=?",Integer.class,permission)==0)throw CatalogService.invalid("Unknown permission");
        if(create)id=db.queryForObject("INSERT INTO roles(name) VALUES (?) RETURNING id",Long.class,name);
        else if(db.update("UPDATE roles SET name=? WHERE id=?",name,id)==0)throw CatalogService.missing();
        db.update("DELETE FROM role_permissions WHERE role_id=?",id);
        for(long permission:permissions)db.update("INSERT INTO role_permissions VALUES (?,?)",id,permission);
        return ResponseEntity.status(create?201:200).body(Map.of("data",role(id)));
    }
    @DeleteMapping("/roles/{id}") public Map<String,Object> deleteRole(@PathVariable long id){CatalogService.require("manage_roles");
        var rows=db.queryForList("SELECT protected_role FROM roles WHERE id=?",id);if(rows.isEmpty())throw CatalogService.missing();
        if(Boolean.TRUE.equals(rows.getFirst().get("protected_role")))throw CatalogService.invalid("Built-in roles cannot be deleted");
        db.update("DELETE FROM roles WHERE id=?",id);return Map.of("success",true);
    }
    Map<String,Object> user(long id){
        var rows=db.queryForList("SELECT id,email,first_name,last_name,language,phone,role,enabled,created_at FROM app_users WHERE id=?",id);if(rows.isEmpty())throw CatalogService.missing();
        var a=new LinkedHashMap<>(rows.getFirst());a.remove("id");a.put("created_at",a.get("created_at").toString());
        a.put("role",db.queryForList("SELECT id,name FROM roles WHERE name=?",a.get("role")));
        var images=db.queryForList("SELECT id FROM media WHERE owner_type='users' AND owner_id=?",id);
        a.put("image_url",images.isEmpty()?"":"/api/media/"+images.getFirst().get("id"));
        return wrap("users",id,a);
    }
    @GetMapping("/users") public Map<String,Object> users(@RequestParam Map<String,String> q){admin();
        int size=CatalogService.integer(q.getOrDefault("page[size]","10")),page=CatalogService.integer(q.getOrDefault("page[number]","1"));
        if(size<0||size>100||page<1)throw CatalogService.invalid("Invalid pagination");
        String pattern="%"+q.getOrDefault("filter[search]","").replace("%","\\%").replace("_","\\_")+"%";
        String where=" WHERE first_name ILIKE ? OR last_name ILIKE ? OR email ILIKE ?";
        long total=db.queryForObject("SELECT count(*) FROM app_users"+where,Long.class,pattern,pattern,pattern);
        var args=new ArrayList<Object>(List.of(pattern,pattern,pattern));String sql="SELECT id FROM app_users"+where+" ORDER BY id";
        if(size>0){sql+=" LIMIT ? OFFSET ?";args.add(size);args.add(((long)page-1)*size);}
        return Map.of("data",db.queryForList(sql,Long.class,args.toArray()).stream().map(this::user).toList(),"meta",Map.of("total",total));
    }
    @GetMapping("/users/{id}") public Map<String,Object> getUser(@PathVariable long id){admin();return Map.of("data",user(id));}
    void password(String password){if(password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)throw CatalogService.invalid("Password must contain at least 12 characters and at most 72 UTF-8 bytes");}
    @Transactional
    Map<String,Object> saveUser(Long id,Map<String,Object> b){
        admin();String first=text(b,"first_name",100,true),last=text(b,"last_name",100,false),email=text(b,"email",254,true).toLowerCase(Locale.ROOT),phone=text(b,"phone",40,false);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw CatalogService.invalid("Invalid email");
        long roleId=CatalogService.positiveId(b.get("role_id"));var roles=db.queryForList("SELECT name FROM roles WHERE id=?",String.class,roleId);
        if(roles.isEmpty())throw CatalogService.invalid("Unknown role");String role=roles.getFirst();
        String pwd=Objects.toString(b.get("password"),"");boolean enabled=!"false".equals(Objects.toString(b.getOrDefault("enabled",true)));
        if(!pwd.isEmpty() || id==null){password(pwd);if(!pwd.equals(Objects.toString(b.get("confirm_password"),"")))throw CatalogService.invalid("Passwords do not match");}
        if(id==null)id=db.queryForObject("INSERT INTO app_users(first_name,last_name,email,phone,password_hash,role,enabled,language) VALUES (?,?,?,?,?,?,?,(SELECT value FROM ui_settings WHERE key='default_language')) RETURNING id",Long.class,first,last,email,phone,encoder.encode(pwd),role,enabled);
        else {
            var old=db.queryForList("SELECT role FROM app_users WHERE id=? FOR UPDATE",id);if(old.isEmpty())throw CatalogService.missing();
            if("ADMIN".equals(old.getFirst().get("role")) && (!role.equals("ADMIN") || !enabled))throw CatalogService.invalid("Administrator accounts cannot be disabled or demoted here");
            db.update("UPDATE app_users SET first_name=?,last_name=?,email=?,phone=?,role=?,enabled=? WHERE id=?",first,last,email,phone,role,enabled,id);
            if(!pwd.isEmpty()){db.update("UPDATE app_users SET password_hash=? WHERE id=?",encoder.encode(pwd),id);db.update("DELETE FROM access_tokens WHERE user_id=?",id);}
            if(!enabled)db.update("DELETE FROM access_tokens WHERE user_id=?",id);
        }
        return Map.of("data",user(id));
    }
    @RequestMapping(value={"/users","/users/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH},consumes="application/json")
    @Transactional public ResponseEntity<?> jsonUser(@PathVariable(required=false) Long id,@RequestBody Map<String,Object> body){return ResponseEntity.status(id==null?201:200).body(saveUser(id,body));}
    @RequestMapping(value={"/users","/users/{id}"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH},consumes="multipart/form-data")
    @Transactional public ResponseEntity<?> formUser(@PathVariable(required=false) Long id,MultipartHttpServletRequest req){
        var body=new LinkedHashMap<String,Object>();req.getParameterMap().forEach((k,v)->body.put(k,v[0]));var result=saveUser(id,body);long saved=((Number)((Map<?,?>)result.get("data")).get("id")).longValue();
        var files=req.getMultiFileMap().values().stream().flatMap(List::stream).filter(f->!f.isEmpty()).toList();if(!files.isEmpty())media.store("users",saved,files);
        return ResponseEntity.status(id==null?201:200).body(Map.of("data",user(saved)));
    }
    @DeleteMapping("/users/{id}") @Transactional public Map<String,Object> deleteUser(@PathVariable long id){admin();
        var roles=db.queryForList("SELECT role FROM app_users WHERE id=? FOR UPDATE",String.class,id);if(roles.isEmpty())throw CatalogService.missing();
        if(roles.getFirst().equals("ADMIN"))throw CatalogService.invalid("Administrator accounts cannot be deleted here");
        db.update("DELETE FROM app_users WHERE id=?",id);db.update("DELETE FROM media WHERE owner_type='users' AND owner_id=?",id);return Map.of("success",true);
    }
    @PatchMapping("/change-password") @Transactional public Map<String,Object> changePassword(@RequestBody Map<String,Object> b){
        long id=identity.current().id();String current=Objects.toString(b.get("current_password"),"");
        if(current.getBytes(StandardCharsets.UTF_8).length>72 || !encoder.matches(current,db.queryForObject("SELECT password_hash FROM app_users WHERE id=?",String.class,id)))throw CatalogService.invalid("Current password is incorrect");
        String pwd=Objects.toString(b.get("password"),"");password(pwd);if(!pwd.equals(Objects.toString(b.get("password_confirmation"),"")))throw CatalogService.invalid("Passwords do not match");
        db.update("UPDATE app_users SET password_hash=? WHERE id=?",encoder.encode(pwd),id);db.update("DELETE FROM access_tokens WHERE user_id=?",id);return Map.of("success",true);
    }
}
