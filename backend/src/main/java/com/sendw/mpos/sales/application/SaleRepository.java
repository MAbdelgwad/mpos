package com.sendw.mpos.sales.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SaleRepository {
    record Header(long customer, long warehouse, LocalDate date, int status, Map<String,Object> totals, String notes, long actor) {}
    record Page(List<Map<String,Object>> rows, long total) {}
    Map<String,Object> find(long id, boolean lock);
    List<Map<String,Object>> lockProduct(long id);
    List<Map<String,Object>> lockUnit(Object id);
    long insert(Header header);
    void replaceHeader(long id, Header header);
    void insertLine(long sale, Map<String,Object> line);
    List<Map<String,Object>> lockLineUnits(long sale);
    List<Map<String,Object>> quantities(long sale);
    void setStatus(long id, int status);
    String customerName(Object id);
    String warehouseName(Object id);
    List<Map<String,Object>> lines(Object sale);
    Page search(String escapedPattern, Integer status, int page, int size);
}
