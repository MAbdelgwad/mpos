package com.sendw.mpos.payments.application;
import java.math.BigDecimal;
import com.sendw.mpos.shared.domain.Requests;
public interface PaymentBalances {
    BigDecimal paid(String documentType,long documentId);
    // Caller must hold the document lock used by payment posting and voiding.
    default void requireUnpaid(String type,long id) {
        if(paid(type,id).signum()!=0)throw Requests.conflict("Void recorded payments before cancelling this document");
    }
}
