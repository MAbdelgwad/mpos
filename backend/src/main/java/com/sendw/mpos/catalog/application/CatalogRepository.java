package com.sendw.mpos.catalog.application;
import com.sendw.mpos.catalog.domain.CatalogSchema.Spec;
import java.util.*;
public interface CatalogRepository {
    record Page(List<Map<String,Object>> rows,long total) {}
    Page search(Spec spec,String pattern,Map<String,Long> filters,String sort,boolean descending,int page,int size);
    List<Map<String,Object>> find(String table,long id);
    String nextProductCode();
    long insert(Spec spec,Map<String,Object> values);
    void update(Spec spec,long id,Map<String,Object> values);
    long lockProductUnit(long id);
    boolean hasMovements(long product);
    int delete(Spec spec,long id);
    long productCount(String kind,Object id);
    List<Map<String,Object>> balances(Object product);
    long count(Spec spec);
}
