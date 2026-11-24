package com.sendw.mpos.warehouses.application;
import java.util.*;
public interface WarehouseRepository {
    record Page(List<Map<String,Object>> rows,long total) {}
    Page search(String pattern,String sort,boolean descending,int page,int size);
    long insert(WarehouseInput input);
    int update(long id,WarehouseInput input);
    int delete(long id);
    List<Map<String,Object>> find(long id);
    long count();
}
