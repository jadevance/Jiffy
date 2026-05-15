package io.github.jadevance.jiffy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class EventContext implements AutoCloseable {

    private final long startNanos = System.nanoTime();
    private final Instant startTimestamp = Instant.now();
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Set<String> suppressedFields = new HashSet<>();
    private final Configuration config;

    private String component;
    private String operation;
    private Level level = Level.INFO;
    private boolean suppressed = false;
    private boolean closed = false;

    public EventContext() {
        this(null, null, null);
    }

    public EventContext(String component, String operation) {
        this(component, operation, null);
    }

    public EventContext(String component, String operation, Configuration config) {
        this.component = component;
        this.operation = operation;
        this.config = config;
    }

    public void initialize(String component, String operation) {
        this.component = component;
        this.operation = operation;
    }

    public EventContext set(String key, Object value) {
        return set(key, value, FieldConflict.OVERWRITE);
    }

    public EventContext set(String key, Object value, FieldConflict behavior) {
        if (fields.containsKey(key)) {
            switch (behavior) {
                case IGNORE:
                    return this;
                case APPEND:
                    fields.put(key, fields.get(key) + "," + value);
                    return this;
                case OVERWRITE:
                default:
                    fields.put(key, value);
                    return this;
            }
        }
        fields.put(key, value);
        return this;
    }

    public EventContext trySet(String key, Supplier<Object> valueFn) {
        try {
            return set(key, valueFn.get());
        } catch (Throwable ignored) {
            return this;
        }
    }

    public TimedScope time(String key) {
        long start = System.nanoTime();
        return () -> fields.put("TimeElapsed_" + key, round1((System.nanoTime() - start) / 1_000_000.0));
    }

    @FunctionalInterface
    public interface TimedScope extends AutoCloseable {
        @Override
        void close();
    }

    public EventContext count(String key) {
        String fieldKey = "Count_" + key;
        Object existing = fields.get(fieldKey);
        long current = existing instanceof Number n ? n.longValue() : 0L;
        fields.put(fieldKey, current + 1);
        return this;
    }

    public boolean contains(String key) {
        return fields.containsKey(key);
    }

    public EventContext appendToValue(String key, String content, String delimiter) {
        Object existing = fields.get(key);
        if (existing == null || existing.toString().isEmpty()) {
            fields.put(key, content);
        } else {
            fields.put(key, existing + delimiter + content);
        }
        return this;
    }

    public EventContext includeException(Throwable t) {
        setToError("An exception has occurred");
        set("Exception_Type", t.getClass().getName());
        set("Exception_Message", String.valueOf(t.getMessage()));
        set("Exception_StackTrace", stackTraceString(t));
        Throwable innermost = t;
        while (innermost.getCause() != null && innermost.getCause() != innermost) {
            innermost = innermost.getCause();
        }
        if (innermost != t) {
            set("InnermostException_Type", innermost.getClass().getName());
            set("InnermostException_Message", String.valueOf(innermost.getMessage()));
            set("InnermostException_StackTrace", stackTraceString(innermost));
        }
        return this;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setToInfo() {
        this.level = Level.INFO;
    }

    public void setToWarning(String reason) {
        this.level = Level.WARNING;
        if (reason != null) set("WarningReason", reason);
    }

    public void setToError(String reason) {
        this.level = Level.ERROR;
        if (reason != null) set("ErrorReason", reason);
    }

    public Level level() {
        return level;
    }

    public String component() {
        return component;
    }

    public String operation() {
        return operation;
    }

    public double elapsedMilliseconds() {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    public void suppress() {
        this.suppressed = true;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void suppressFields(String... names) {
        for (String n : names) suppressedFields.add(n);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (suppressed) return;

        Map<String, Object> out = new LinkedHashMap<>(GlobalEventContext.instance().snapshot());
        out.put("Level", level.pretty());
        if (component != null) out.put("Component", component);
        if (operation != null) out.put("Operation", operation);
        out.put("TimeElapsed", round1(elapsedMilliseconds()));
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (suppressedFields.contains(e.getKey())) continue;
            out.put(e.getKey(), e.getValue());
        }

        Configuration cfg = config != null ? config : Configuration.active();
        cfg.emit(new EventEmission(startTimestamp, level, out));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String stackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
