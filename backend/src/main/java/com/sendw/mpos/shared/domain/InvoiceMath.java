package com.sendw.mpos.shared.domain;
import com.sendw.mpos.shared.domain.Requests;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import java.math.*;
public final class InvoiceMath {
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    private InvoiceMath() {}
    public static BigDecimal money(BigDecimal n){var rounded=n.setScale(4,RoundingMode.HALF_UP);if(rounded.precision()-rounded.scale()>15)throw Validation.invalid("Calculated amount exceeds supported precision");return rounded;}
    public static Map<String,Object> line(Map<String,Object> input,String priceField,String unitField,String netField){
        BigDecimal cost=Requests.nonnegative(input,priceField,true),quantity=Requests.number(input,"quantity",true);
        if(quantity.signum()<=0)throw Validation.invalid("Quantity must be positive");
        BigDecimal discount=Requests.nonnegative(input,"discount_value",false),tax=Requests.rate(input,"tax_value");
        int discountType=Requests.choice(input,"discount_type",2,2),taxType=Requests.choice(input,"tax_type",1,2);
        if(discount.compareTo(discountType==1?HUNDRED:cost)>0)throw Validation.invalid("Discount exceeds allowed value");
        BigDecimal perDiscount=discountType==1?cost.multiply(discount).divide(HUNDRED):discount;
        BigDecimal discounted=cost.subtract(perDiscount),base,taxAmount,total;
        if(taxType==1){base=discounted;taxAmount=money(base.multiply(tax).multiply(quantity).divide(HUNDRED));total=money(base.multiply(quantity).add(taxAmount));}
        else{base=discounted.multiply(HUNDRED).divide(HUNDRED.add(tax),8,RoundingMode.HALF_UP);total=money(discounted.multiply(quantity));taxAmount=money(discounted.multiply(tax).multiply(quantity).divide(HUNDRED.add(tax),4,RoundingMode.HALF_UP));}
        var out=new LinkedHashMap<String,Object>();out.put("product_id",Requests.id(input,"product_id"));
        Object unit=input.get(unitField);out.put(unitField,unit==null||unit.toString().isBlank()?null:Validation.positiveId(unit));
        out.put(priceField,cost);out.put("quantity",quantity);out.put("discount_type",discountType);out.put("discount_value",discount);out.put("tax_type",taxType);out.put("tax_value",tax);
        out.put(netField,base.setScale(8,RoundingMode.HALF_UP));out.put("discount_amount",money(perDiscount.multiply(quantity)));out.put("tax_amount",taxAmount);out.put("sub_total",total);return out;
    }
    public static Map<String,Object> totals(Map<String,Object> body,List<Map<String,Object>> lines){
        BigDecimal subtotal=lines.stream().map(x->(BigDecimal)x.get("sub_total")).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal discount=Requests.nonnegative(body,"discount",false),shipping=Requests.nonnegative(body,"shipping",false),rate=Requests.rate(body,"tax_rate");
        if(discount.compareTo(subtotal)>0)throw Validation.invalid("Invoice discount exceeds subtotal");
        BigDecimal taxable=subtotal.subtract(discount),tax=money(taxable.multiply(rate).divide(HUNDRED)),total=taxable.add(tax);
        if(shipping.compareTo(total)>0)throw Validation.invalid("Shipping exceeds the invoice total before shipping");
        return Map.of("discount",discount,"shipping",shipping,"tax_rate",rate,"tax_amount",tax,"grand_total",money(total.add(shipping)));
    }
}
