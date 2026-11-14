package com.sendw.mpos.identity.application;
import com.sendw.mpos.identity.application.IdentityService;
import com.sendw.mpos.media.application.MediaService;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.identity.application.Permissions;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.sendw.mpos.media.application.UploadedImage;

@org.springframework.stereotype.Service
public class AccessService {
    private final AccessRepository repository;private final IdentityService identity;private final MediaService media;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    public AccessService(AccessRepository repository,IdentityService identity,MediaService media){this.repository=repository;this.identity=identity;this.media=media;}
    void admin(){Permissions.require("manage_users");}
    static String text(Map<String,Object> b,String key,int max,boolean required){
        String value=Objects.toString(b.get(key),"").trim();if((required && value.isEmpty()) || value.length()>max)throw Validation.invalid("Invalid "+key);return value;
    }
    static Map<String,Object> wrap(String type,Object id,Map<String,Object> a){return Map.of("id",id,"type",type,"attributes",a);}
     public Map<String,Object> permissions(){
        var data=repository.permissions().stream().map(p->wrap("permissions",p.get("id"),Map.of("name",p.get("name"),"display_name",p.get("display_name")))).toList();
        return Map.of("data",data);
    }
    Map<String,Object> role(long id){
        var rows=repository.role(id);if(rows.isEmpty())throw Validation.missing();var row=rows.getFirst();
        return wrap("roles",id,Map.of("name",row.get("name"),"display_name",row.get("name"),"is_default",row.get("protected_role"),"created_at",row.get("created_at").toString(),"permissions",repository.rolePermissions(id)));
    }
     public Map<String,Object> roles(){Permissions.require("manage_roles");var data=repository.roleIds().stream().map(this::role).toList();return Map.of("data",data,"meta",Map.of("total",data.size()));}
     public Map<String,Object> getRole( long id){Permissions.require("manage_roles");return Map.of("data",role(id));}

    @Transactional
    public Map<String,Object> saveRole( Long id, Map<String,Object> body){
        Permissions.require("manage_roles");boolean create=id==null;String name=text(body,"name",50,true);
        if(id!=null)role(id);
        if(id!=null && Boolean.TRUE.equals(repository.role(id).getFirst().get("protected_role")))throw Validation.invalid("Built-in roles cannot be changed");
        if(!(body.get("permissions") instanceof List<?> raw))throw Validation.invalid("permissions must be an array");
        var permissions=raw.stream().map(Validation::positiveId).distinct().toList();
        for(long permission:permissions)if(!repository.permissionExists(permission))throw Validation.invalid("Unknown permission");
        if(create)id=repository.createRole(name);
        else if(repository.renameRole(id,name)==0)throw Validation.missing();
        repository.replacePermissions(id,permissions);
        return Map.of("data",role(id));
    }
     public Map<String,Object> deleteRole( long id){Permissions.require("manage_roles");
        var rows=repository.role(id);if(rows.isEmpty())throw Validation.missing();
        if(Boolean.TRUE.equals(rows.getFirst().get("protected_role")))throw Validation.invalid("Built-in roles cannot be deleted");
        repository.deleteRole(id);return Map.of("success",true);
    }
    Map<String,Object> user(long id){
        var rows=repository.user(id);if(rows.isEmpty())throw Validation.missing();
        var a=new LinkedHashMap<>(rows.getFirst());a.remove("id");a.put("created_at",a.get("created_at").toString());
        a.put("role",repository.roleByName(a.get("role")));
        var images=media.references("users",id);
        a.put("image_url",images.isEmpty()?"":"/api/media/"+images.getFirst());
        return wrap("users",id,a);
    }
     public Map<String,Object> users( Map<String,String> q){admin();
        int size=Validation.integer(q.getOrDefault("page[size]","10")),page=Validation.integer(q.getOrDefault("page[number]","1"));
        if(size<0||size>100||page<1)throw Validation.invalid("Invalid pagination");
        String pattern="%"+q.getOrDefault("filter[search]","").replace("%","\\%").replace("_","\\_")+"%";
        var result=repository.users(pattern,page,size);
        return Map.of("data",result.ids().stream().map(this::user).toList(),"meta",Map.of("total",result.total()));
    }
     public Map<String,Object> getUser( long id){admin();return Map.of("data",user(id));}
    void password(String password){if(password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)throw Validation.invalid("Password must contain at least 12 characters and at most 72 UTF-8 bytes");}
    @Transactional
    Map<String,Object> saveUser(Long id,Map<String,Object> b){
        admin();String first=text(b,"first_name",100,true),last=text(b,"last_name",100,false),email=text(b,"email",254,true).toLowerCase(Locale.ROOT),phone=text(b,"phone",40,false);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw Validation.invalid("Invalid email");
        long roleId=Validation.positiveId(b.get("role_id"));var roles=repository.roleNames(roleId);
        if(roles.isEmpty())throw Validation.invalid("Unknown role");String role=roles.getFirst();
        String pwd=Objects.toString(b.get("password"),"");boolean enabled=!"false".equals(Objects.toString(b.getOrDefault("enabled",true)));
        if(!pwd.isEmpty() || id==null){password(pwd);if(!pwd.equals(Objects.toString(b.get("confirm_password"),"")))throw Validation.invalid("Passwords do not match");}
        if(id==null)id=repository.createUser(new AccessRepository.UserFields(first,last,email,phone,role,enabled),encoder.encode(pwd));
        else {
            var old=repository.lockUserRole(id);if(old.isEmpty())throw Validation.missing();
            if("ADMIN".equals(old.getFirst()) && (!role.equals("ADMIN") || !enabled))throw Validation.invalid("Administrator accounts cannot be disabled or demoted here");
            repository.updateUser(id,new AccessRepository.UserFields(first,last,email,phone,role,enabled));
            if(!pwd.isEmpty()){repository.setPassword(id,encoder.encode(pwd));repository.revokeUserTokens(id);}
            if(!enabled)repository.revokeUserTokens(id);
        }
        return Map.of("data",user(id));
    }

    @Transactional public Map<String,Object> jsonUser(Long id, Map<String,Object> body) { return saveUser(id,body); }

    @Transactional public Map<String,Object> formUser(Long id, Map<String,Object> body, List<UploadedImage> files) {
        var result=saveUser(id,body);long saved=((Number)((Map<?,?>)result.get("data")).get("id")).longValue();
        if(!files.isEmpty())media.store("users",saved,files);
        return Map.of("data",user(saved));
    }
     @Transactional public Map<String,Object> deleteUser( long id){admin();
        var roles=repository.lockUserRole(id);if(roles.isEmpty())throw Validation.missing();
        if(roles.getFirst().equals("ADMIN"))throw Validation.invalid("Administrator accounts cannot be deleted here");
        repository.deleteUser(id);media.deleteOwned("users",id);return Map.of("success",true);
    }
     @Transactional public Map<String,Object> changePassword(PasswordChange b){
        long id=identity.current().id();String current=Objects.toString(b.current_password(),"");
        if(current.getBytes(StandardCharsets.UTF_8).length>72 || !encoder.matches(current,repository.passwordHash(id)))throw Validation.invalid("Current password is incorrect");
        String pwd=Objects.toString(b.password(),"");password(pwd);if(!pwd.equals(Objects.toString(b.password_confirmation(),"")))throw Validation.invalid("Passwords do not match");
        repository.setPassword(id,encoder.encode(pwd));repository.revokeUserTokens(id);return Map.of("success",true);
    }
}
