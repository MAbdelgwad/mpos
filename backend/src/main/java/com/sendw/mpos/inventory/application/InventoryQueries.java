package com.sendw.mpos.inventory.application;
import java.util.List;
import java.util.Map;
public interface InventoryQueries {
    record Page(List<Map<String,Object>> rows, long total) {}
    boolean lockProduct(long id);
    Page search(boolean ledger, Long warehouse, Long product, int page, int size);
}
