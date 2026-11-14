package com.sendw.mpos.inventory.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Persistence port; all operations participate in the caller's transaction. */
public interface StockRepository {
    BigDecimal lockBalance(long warehouse, long product);
    boolean hasMovements(long warehouse, long product);
    void createOperation(UUID id, String kind, Long purchase, Long sale, String reason, long actor);
    void recordMovement(UUID operation, long warehouse, long product, BigDecimal delta, BigDecimal balance);
}
