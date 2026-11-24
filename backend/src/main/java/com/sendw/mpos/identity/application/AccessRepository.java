package com.sendw.mpos.identity.application;
import java.util.*;
public interface AccessRepository {
    record UserFields(String first, String last, String email, String phone, String role, boolean enabled) {}
    record UserPage(List<Long> ids, long total) {}
    List<Map<String,Object>> permissions();
    List<Map<String,Object>> role(long id);
    List<Map<String,Object>> rolePermissions(long id);
    List<Long> roleIds();
    boolean permissionExists(long id);
    long createRole(String name);
    int renameRole(long id,String name);
    void replacePermissions(long role,List<Long> permissions);
    void deleteRole(long id);
    List<Map<String,Object>> user(long id);
    List<Map<String,Object>> roleByName(Object name);
    List<String> roleNames(long id);
    UserPage users(String pattern,int page,int size);
    long createUser(UserFields fields,String passwordHash);
    List<String> lockUserRole(long id);
    void updateUser(long id,UserFields fields);
    void setPassword(long id,String hash);
    void revokeUserTokens(long id);
    void deleteUser(long id);
    String passwordHash(long id);
}
