package com.sendw.mpos.sales.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public input contract. Client-computed totals are intentionally not accepted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaleRequest(Long customer_id, Long warehouse_id, String date, Integer status, Integer version,
                          BigDecimal discount, BigDecimal tax_rate, BigDecimal shipping, String notes, String note,
                          BigDecimal paid_amount, BigDecimal received_amount, Integer payment_status,
                          List<Line> sale_items) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(Long product_id, BigDecimal product_price, BigDecimal quantity, Long sale_unit,
                       Integer discount_type, BigDecimal discount_value, Integer tax_type, BigDecimal tax_value) {
        Map<String,Object> values() {
            var result=new LinkedHashMap<String,Object>();
            result.put("product_id",product_id); result.put("product_price",product_price);
            result.put("quantity",quantity); result.put("sale_unit",sale_unit);
            result.put("discount_type",discount_type); result.put("discount_value",discount_value);
            result.put("tax_type",tax_type); result.put("tax_value",tax_value);
            return result;
        }
    }
    public Map<String,Object> values() {
        var result=new LinkedHashMap<String,Object>();
        result.put("customer_id",customer_id); result.put("warehouse_id",warehouse_id);
        result.put("date",date); result.put("status",status);
        if(version!=null)result.put("version",version);
        result.put("discount",discount); result.put("tax_rate",tax_rate); result.put("shipping",shipping);
        result.put("notes",notes==null?note:notes);
        result.put("paid_amount",paid_amount); result.put("received_amount",received_amount);
        result.put("payment_status",payment_status);
        if(sale_items!=null) {
            if(sale_items.stream().anyMatch(java.util.Objects::isNull))throw com.sendw.mpos.shared.domain.Validation.invalid("Invalid sale item");
            result.put("sale_items",sale_items.stream().map(Line::values).toList());
        }
        return result;
    }
}
