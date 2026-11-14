package com.sendw.mpos;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
final class Requests {
    private Requests() {}
    static BigDecimal number(Map<String,Object> body,String key,boolean required) {
        Object value=body.get(key);if(value==null || value.toString().isBlank()){if(required)throw CatalogService.invalid(key+" is required");return BigDecimal.ZERO;}
        try {BigDecimal n=new BigDecimal(value.toString());if(n.scale()>4 || n.precision()-n.scale()>12)throw CatalogService.invalid("Invalid precision for "+key);return n;}
        catch(NumberFormatException e){throw CatalogService.invalid("Invalid "+key);}
    }
    static BigDecimal nonnegative(Map<String,Object> b,String k,boolean required){var n=number(b,k,required);if(n.signum()<0)throw CatalogService.invalid(k+" must not be negative");return n;}
    static BigDecimal rate(Map<String,Object>b,String k){var n=nonnegative(b,k,false);if(n.compareTo(new BigDecimal("100"))>0)throw CatalogService.invalid(k+" must be at most 100");return n;}
    static String text(Map<String,Object>b,String k,int limit,boolean required){String s=Objects.toString(b.get(k),"").trim();if((required&&s.isBlank())||s.length()>limit)throw CatalogService.invalid("Invalid "+k);return s;}
    static long id(Map<String,Object>b,String k){return CatalogService.positiveId(b.get(k));}
    static int choice(Map<String,Object>b,String k,int fallback,int max){int n=CatalogService.integer(Objects.toString(b.get(k),""+fallback));if(n<1||n>max)throw CatalogService.invalid("Invalid "+k);return n;}
    static LocalDate date(Map<String,Object>b){try{return LocalDate.parse(text(b,"date",10,true));}catch(Exception e){throw CatalogService.invalid("date must be YYYY-MM-DD");}}
    static List<Map<String,Object>> items(Map<String,Object>b,String k){
        if(!(b.get(k) instanceof List<?> list)||list.isEmpty()||list.size()>200)throw CatalogService.invalid("Provide 1–200 "+k);
        var result=new ArrayList<Map<String,Object>>();for(Object row:list){if(!(row instanceof Map<?,?> m))throw CatalogService.invalid("Invalid item");var out=new LinkedHashMap<String,Object>();m.forEach((key,value)->out.put(key.toString(),value));result.add(out);}return result;
    }
    static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
