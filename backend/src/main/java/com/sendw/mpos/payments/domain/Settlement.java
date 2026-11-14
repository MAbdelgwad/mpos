package com.sendw.mpos.payments.domain;
import java.math.BigDecimal;
import com.sendw.mpos.shared.domain.Requests;
public record Settlement(BigDecimal paid, BigDecimal due, int status) {
    public static Settlement of(BigDecimal total,BigDecimal paid,boolean cancelled) {
        BigDecimal due=cancelled?BigDecimal.ZERO:total.subtract(paid);
        return new Settlement(paid,due,due.signum()==0?1:paid.signum()==0?2:3);
    }
    public void accept(BigDecimal amount) {
        if(amount.compareTo(due)>0)throw Requests.conflict("Payment exceeds outstanding amount");
    }
}
