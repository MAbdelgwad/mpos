package com.sendw.mpos;
import com.sendw.mpos.purchasing.domain.PurchaseMath;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PurchaseMathTest {
    Map<String,Object> input(){return new HashMap<>(Map.of("product_id",1,"product_cost","115","quantity","2","discount_type",2,"discount_value","5","tax_type",1,"tax_value","10"));}
    @Test void exclusiveTaxAndFixedUnitDiscountThenHeaderTax(){
        var line=PurchaseMath.line(input());assertEquals(new BigDecimal("242.0000"),line.get("sub_total"));assertEquals(new BigDecimal("10.0000"),line.get("discount_amount"));
        var totals=PurchaseMath.totals(Map.of("discount","12","tax_rate","15","shipping","10"),List.of(line));assertEquals(new BigDecimal("274.5000"),totals.get("grand_total"));
    }
    @Test void inclusiveTaxDoesNotAddTaxTwice(){var in=input();in.put("discount_value","0");in.put("tax_type",2);in.put("tax_value","15");var line=PurchaseMath.line(in);assertEquals(new BigDecimal("230.0000"),line.get("sub_total"));assertEquals(new BigDecimal("30.0000"),line.get("tax_amount"));assertEquals(new BigDecimal("100.00000000"),line.get("net_unit_cost"));}
    @Test void percentageDiscountAndFractionalQuantity(){var in=input();in.put("product_cost","100");in.put("quantity","1.5");in.put("discount_type",1);in.put("discount_value","10");var line=PurchaseMath.line(in);assertEquals(new BigDecimal("148.5000"),line.get("sub_total"));assertEquals(new BigDecimal("15.0000"),line.get("discount_amount"));}
    @Test void invalidRatesAndDiscountsAreRejected(){var in=input();in.put("tax_value","101");assertThrows(com.sendw.mpos.shared.domain.BusinessException.class,()->PurchaseMath.line(in));in.put("tax_value","0");in.put("discount_value","116");assertThrows(com.sendw.mpos.shared.domain.BusinessException.class,()->PurchaseMath.line(in));}
    @Test void inclusiveTaxRoundsTheLineRatherThanTheUnit(){var in=input();in.put("product_cost","1");in.put("quantity","1000000");in.put("discount_value","0");in.put("tax_type",2);in.put("tax_value","15");var line=PurchaseMath.line(in);assertEquals(new BigDecimal("130434.7826"),line.get("tax_amount"));assertEquals(new BigDecimal("1000000.0000"),line.get("sub_total"));}
}
