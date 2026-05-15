package io.github.jadevance.jiffy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalEventContext {

    private static final GlobalEventContext INSTANCE = new GlobalEventContext();

    private final Map<String, Object> fields = new ConcurrentHashMap<>();

    private GlobalEventContext() {}

    public static GlobalEventContext instance() {
        return INSTANCE;
    }

    public GlobalEventContext set(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    public Map<String, Object> snapshot() {
        return new LinkedHashMap<>(fields);
    }
}
