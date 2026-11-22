package com.sendw.mpos.catalog.domain;
import java.util.*;
public final class CatalogSchema {
private CatalogSchema() {}
    public record Spec(String table,String permission,Set<String> fields,Set<String> required) {
        public Spec { fields=Collections.unmodifiableSet(new LinkedHashSet<>(fields));required=Set.copyOf(required); }
    }
    static Spec spec(String table,String permission,String fields,String required) {
        return new Spec(table,permission,new LinkedHashSet<>(List.of(fields.split(","))),Set.of(required.split(",")));
    }
    public static final Map<String,Spec> SPECS=Map.of(
        "brands",spec("brands","manage_brands","name,description","name"),
        "product-categories",spec("product_categories","manage_product_categories","name,description","name"),
        "units",spec("units","manage_units","name,short_name","name,short_name"),
        "customers",spec("customers","manage_customers","name,email,phone,country,city,address,dob","name,email,phone,country,city,address"),
        "suppliers",spec("suppliers","manage_suppliers","name,email,phone,country,city,address","name,email,phone,country,city,address"),
        "products",spec("products","manage_products","name,code,product_category_id,brand_id,product_cost,product_price,product_unit,sale_unit,purchase_unit,stock_alert,quantity_limit,order_tax,tax_type,barcode_symbol,notes","name,code,product_category_id,brand_id,product_cost,product_price,product_unit,barcode_symbol"));
    public static Spec table(String table) { return SPECS.values().stream().filter(s->s.table().equals(table)).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported catalog table")); }
}
