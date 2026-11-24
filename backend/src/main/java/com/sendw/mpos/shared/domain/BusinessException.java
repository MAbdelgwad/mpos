package com.sendw.mpos.shared.domain;
public final class BusinessException extends RuntimeException {
    public enum Kind { INVALID, NOT_FOUND, CONFLICT }
    private final Kind kind;
    public BusinessException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
}
