package com.sendw.mpos.shared.domain;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.shared.domain.BusinessException;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class Requests {
    private Requests() {}
    public static BigDecimal number(Map<String,Object> body,String key,boolean required) {
        Object value=body.get(key);if(value==null || value.toString().isBlank()){if(required)throw Validation.invalid(key+" is required");return BigDecimal.ZERO;}
        try {BigDecimal n=new BigDecimal(value.toString());if(n.scale()>4 || n.precision()-n.scale()>12)throw Validation.invalid("Invalid precision for "+key);return n;}
        catch(NumberFormatException e){throw Validation.invalid("Invalid "+key);}
    }
    public static BigDecimal nonnegative(Map<String,Object> b,String k,boolean required){var n=number(b,k,required);if(n.signum()<0)throw Validation.invalid(k+" must not be negative");return n;}
    public static BigDecimal rate(Map<String,Object>b,String k){var n=nonnegative(b,k,false);if(n.compareTo(new BigDecimal("100"))>0)throw Validation.invalid(k+" must be at most 100");return n;}
    public static String text(Map<String,Object>b,String k,int limit,boolean required){String s=Objects.toString(b.get(k),"").trim();if((required&&s.isBlank())||s.length()>limit)throw Validation.invalid("Invalid "+k);return s;}
    public static long id(Map<String,Object>b,String k){return Validation.positiveId(b.get(k));}
    public static int choice(Map<String,Object>b,String k,int fallback,int max){int n=Validation.integer(Objects.toString(b.get(k),""+fallback));if(n<1||n>max)throw Validation.invalid("Invalid "+k);return n;}
    public static LocalDate date(Map<String,Object>b){try{return LocalDate.parse(text(b,"date",10,true));}catch(Exception e){throw Validation.invalid("date must be YYYY-MM-DD");}}
    public static List<Map<String,Object>> items(Map<String,Object>b,String k){
        if(!(b.get(k) instanceof List<?> list)||list.isEmpty()||list.size()>200)throw Validation.invalid("Provide 1–200 "+k);
        var result=new ArrayList<Map<String,Object>>();for(Object row:list){if(!(row instanceof Map<?,?> m))throw Validation.invalid("Invalid item");var out=new LinkedHashMap<String,Object>();m.forEach((key,value)->out.put(key.toString(),value));result.add(out);}return result;
    }
    public static BusinessException conflict(String message){return new BusinessException(BusinessException.Kind.CONFLICT,message);}
}
