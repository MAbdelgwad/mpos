package com.sendw.mpos.purchasing.application;

import java.sql.Date;
import java.util.List;
import java.util.Map;

public interface PurchaseRepository {
    record Header(long supplier, long warehouse, Date date, int status, Map<String,Object> totals, String notes, long actor) {}
    record Page(List<Map<String,Object>> rows, long total) {}
    Map<String,Object> find(long id, boolean lock);
    List<Map<String,Object>> lockProduct(long id);
    List<Map<String,Object>> lockUnit(Object id);
    long insert(Header header);
    void replaceHeader(long id, Header header);
    void insertLine(long purchase, Map<String,Object> line);
    List<Map<String,Object>> lockLineUnits(long purchase);
    List<Map<String,Object>> quantities(long purchase);
    void setStatus(long id, int status);
    String supplierName(Object id);
    String warehouseName(Object id);
    List<Map<String,Object>> lines(Object purchase);
    Page search(String escapedPattern, Integer status, int page, int size);
}
