package com.sendw.mpos.inventory.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public application boundary used by purchasing and future sales workflows. */
public interface StockOperations {
    record Delta(long warehouse, long product, BigDecimal quantity) {}
    UUID applySale(String kind,long sale,String reason,List<Delta> deltas);
    UUID apply(String kind, Long purchase, String reason, List<Delta> deltas);
}
