package com.sendw.mpos.shared.domain;

public final class Validation {
private Validation() {}
    public static BusinessException invalid(String message) {return new BusinessException(BusinessException.Kind.INVALID,message);}
    public static BusinessException missing() {return new BusinessException(BusinessException.Kind.NOT_FOUND,"Record not found");}
    public static int integer(String s) {try{return Integer.parseInt(s);}catch(Exception e){throw invalid("Invalid integer");}}
    public static long positiveId(Object v) {if(v==null)throw invalid("Missing relation ID");try{long id=Long.parseLong(v.toString());if(id<1)throw invalid("Invalid relation ID");return id;}catch(NumberFormatException e){throw invalid("Invalid relation ID");}}
}
