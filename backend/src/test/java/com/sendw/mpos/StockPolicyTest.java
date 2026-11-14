package com.sendw.mpos;

import com.sendw.mpos.inventory.domain.StockPolicy;
import com.sendw.mpos.shared.domain.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockPolicyTest {
    @Test void permitsExactDepletionAndFractionalReceipts() {
        assertEquals(BigDecimal.ZERO, StockPolicy.nextBalance(new BigDecimal("3"), new BigDecimal("-3"), 1, 1));
        assertEquals(new BigDecimal("3.1250"), StockPolicy.nextBalance(new BigDecimal("3"), new BigDecimal("0.1250"), 1, 1));
    }
    @Test void rejectsOverdrawAndZeroMovements() {
        var overdraw = assertThrows(BusinessException.class, () -> StockPolicy.nextBalance(BigDecimal.ONE, new BigDecimal("-2"), 1, 1));
        assertEquals(BusinessException.Kind.CONFLICT, overdraw.kind());
        var zero = assertThrows(BusinessException.class, () -> StockPolicy.nextBalance(BigDecimal.ONE, BigDecimal.ZERO, 1, 1));
        assertEquals(BusinessException.Kind.INVALID, zero.kind());
    }
}
