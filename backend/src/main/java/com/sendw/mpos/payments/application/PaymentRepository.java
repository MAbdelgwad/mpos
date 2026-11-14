package com.sendw.mpos.payments.application;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public interface PaymentRepository extends PaymentBalances {
    record Document(int status,BigDecimal total) {}
    Document document(String type,long id,boolean lock);
    List<Map<String,Object>> list(String type,long id);
    Map<String,Object> find(String type,long document,long payment);
    long insert(String type,long id,BigDecimal amount,LocalDate date,String method,String reference,String notes,long actor);
    void voidPayment(long id,String reason,long actor);
}
