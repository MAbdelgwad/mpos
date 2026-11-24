package com.sendw.mpos.settings.application;
import java.util.Map;
public interface SettingsRepository {
    Map<String,String> settings();
    void update(String key,String value);
}
