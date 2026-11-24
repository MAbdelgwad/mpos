package com.sendw.mpos.imports.domain;
import java.math.*;
import java.util.*;
import com.sendw.mpos.shared.domain.Validation;
public final class ImportRules {
 private ImportRules(){}
 public static long pieces(Object value){try{long n=new BigDecimal(Objects.toString(value,"")).longValueExact();if(n<1||n>1000000000)throw new IllegalArgumentException();return n;}catch(Exception e){throw Validation.invalid("عدد القطع يجب أن يكون عددًا صحيحًا موجبًا / Pieces must be a positive integer");}}
 public static BigDecimal rate(Object value,String currency){try{var n=new BigDecimal(Objects.toString(value,""));if(n.signum()<=0||n.scale()>8||n.compareTo(new BigDecimal("1000000"))>0||currency.equals("SAR")&&n.compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException();return n;}catch(Exception e){throw Validation.invalid("سعر التحويل غير صالح؛ الريال = 1 / Invalid exchange rate; SAR must be 1");}}
 public static BigDecimal sar(BigDecimal amount,BigDecimal rate){var result=amount.multiply(rate).setScale(4,RoundingMode.HALF_UP);if(result.signum()<=0||result.precision()>23)throw Validation.invalid("Expense total outside supported range");return result;}
}
