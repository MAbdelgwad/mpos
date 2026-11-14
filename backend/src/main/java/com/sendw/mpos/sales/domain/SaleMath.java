package com.sendw.mpos.sales.domain;
import com.sendw.mpos.shared.domain.InvoiceMath;
import java.math.BigDecimal;
import java.util.*;
public final class SaleMath {
    private SaleMath() {}
    public static BigDecimal money(BigDecimal amount) { return InvoiceMath.money(amount); }
    public static Map<String,Object> line(Map<String,Object> input) { return InvoiceMath.line(input,"product_price","sale_unit","net_unit_price"); }
    public static Map<String,Object> totals(Map<String,Object> input,List<Map<String,Object>> lines) { return InvoiceMath.totals(input,lines); }
}
