package com.sendw.mpos.payments.application;
import java.math.BigDecimal;
import java.util.*;
public record PaymentRequest(BigDecimal amount,String date,String method,String reference,String notes) {
    public Map<String,Object> values(){var m=new LinkedHashMap<String,Object>();m.put("amount",amount);m.put("date",date);m.put("method",method);m.put("reference",reference);m.put("notes",notes);return m;}
}
