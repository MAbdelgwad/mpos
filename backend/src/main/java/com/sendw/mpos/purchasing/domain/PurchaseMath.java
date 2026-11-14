package com.sendw.mpos.purchasing.domain;
import com.sendw.mpos.shared.domain.InvoiceMath;
import java.math.BigDecimal;
import java.util.*;
public final class PurchaseMath {
    private PurchaseMath() {}
    public static BigDecimal money(BigDecimal amount) { return InvoiceMath.money(amount); }
    public static Map<String,Object> line(Map<String,Object> input) { return InvoiceMath.line(input,"product_cost","purchase_unit","net_unit_cost"); }
    public static Map<String,Object> totals(Map<String,Object> input,List<Map<String,Object>> lines) { return InvoiceMath.totals(input,lines); }
}
