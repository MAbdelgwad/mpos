package com.sendw.mpos.shared.application;
import java.util.Map;
import java.util.function.Supplier;
public interface RequestDeduplicator {
    Map<String,Object> execute(String scope, String key, Map<String,Object> body, Supplier<Map<String,Object>> action);
}
