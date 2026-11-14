package com.sendw.mpos.inventory.domain;

import com.sendw.mpos.shared.domain.BusinessException;
import java.math.BigDecimal;

/** Stock rules independent of HTTP, Spring and PostgreSQL. */
public final class StockPolicy {
    private StockPolicy() {}

    public static BigDecimal nextBalance(BigDecimal current, BigDecimal delta, long warehouse, long product) {
        if (delta.signum() == 0) {
            throw new BusinessException(BusinessException.Kind.INVALID, "Zero stock movement");
        }
        BigDecimal next = current.add(delta);
        if (next.signum() < 0) {
            throw new BusinessException(BusinessException.Kind.CONFLICT,
                    "Insufficient stock for product " + product + " in warehouse " + warehouse);
        }
        return next;
    }
}
