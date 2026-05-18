package io.github.jadevance.jiffy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class EventContext implements AutoCloseable {

    private final long startNanos = System.nanoTime();
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Configuration config;

    private Map<String, Object> privateData;
    private Map<String, TimedScope> timers;
    private Map<String, Long> counters;
    private Set<String> suppressedFields;

    private Instant startTimestamp = Instant.now();
    private String component;
    private String operation;
    private Level level = Level.INFO;
    private String reason;
    private Throwable capturedException;
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

    public EventContext initialize(String component, String operation) {
        this.component = component;
        this.operation = operation;
        return this;
    }

    public EventContext set(String key, Object value) {
        return set(key, value, FieldConflict.OVERWRITE);
    }

    public EventContext set(String key, Object value, FieldConflict behavior) {
        if (fields.containsKey(key)) {
            if (behavior == FieldConflict.IGNORE) return this;
            if (behavior == FieldConflict.APPEND) {
                fields.put(key, fields.get(key) + "," + value);
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
        TimedScopeImpl scope = new TimedScopeImpl();
        if (timers == null) timers = new LinkedHashMap<>();
        timers.put(key, scope);
        return scope;
    }

    public Map<String, TimedScope> timers() {
        return timers == null ? Map.of() : Collections.unmodifiableMap(timers);
    }

    public EventContext count(String key) {
        if (counters == null) counters = new LinkedHashMap<>();
        counters.merge(key, 1L, Long::sum);
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

    public EventContext setPrivate(String key, Object value) {
        if (privateData == null) privateData = new LinkedHashMap<>();
        privateData.put(key, value);
        return this;
    }

    public Object getPrivate(String key) {
        return privateData == null ? null : privateData.get(key);
    }

    public boolean containsPrivate(String key) {
        return privateData != null && privateData.containsKey(key);
    }

    public Map<String, Object> privateData() {
        return privateData == null ? Map.of() : Collections.unmodifiableMap(privateData);
    }

    public EventContext setCustomTimestamp(Instant timestamp) {
        this.startTimestamp = Objects.requireNonNull(timestamp, "timestamp");
        return this;
    }

    public Instant timestamp() {
        return startTimestamp;
    }

    public EventContext includeException(Throwable t) {
        setToError("An exception has occurred");
        this.capturedException = t;
        return this;
    }

    public EventContext setLevel(Level level) {
        this.level = level;
        return this;
    }

    public EventContext setToInfo() {
        this.level = Level.INFO;
        this.reason = null;
        return this;
    }

    public EventContext setToWarning(String reason) {
        this.level = Level.WARNING;
        this.reason = reason;
        return this;
    }

    public EventContext setToError(String reason) {
        this.level = Level.ERROR;
        this.reason = reason;
        return this;
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

    public EventContext suppress() {
        this.suppressed = true;
        return this;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public EventContext suppressFields(String... names) {
        if (suppressedFields == null) suppressedFields = new HashSet<>();
        Collections.addAll(suppressedFields, names);
        return this;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (suppressed) return;

        Configuration cfg = config != null ? config : Configuration.active();
        NamingConvention naming = cfg.naming();

        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(GlobalEventContext.instance().snapshot());
        out.put(naming.level(), level.pretty());
        if (component != null) out.put(naming.component(), component);
        if (operation != null) out.put(naming.operation(), operation);
        out.put(naming.timeElapsed(), round1(elapsedMilliseconds()));

        if (reason != null) {
            String reasonKey = (level == Level.WARNING) ? naming.warningReason() : naming.errorReason();
            out.put(reasonKey, reason);
        }

        if (capturedException != null) {
            Throwable t = capturedException;
            out.put(naming.exceptionType(), t.getClass().getName());
            out.put(naming.exceptionMessage(), String.valueOf(t.getMessage()));
            out.put(naming.exceptionStackTrace(), stackTraceString(t));
            Throwable innermost = t;
            while (innermost.getCause() != null && innermost.getCause() != innermost) {
                innermost = innermost.getCause();
            }
            if (innermost != t) {
                out.put(naming.innermostExceptionType(), innermost.getClass().getName());
                out.put(naming.innermostExceptionMessage(), String.valueOf(innermost.getMessage()));
                out.put(naming.innermostExceptionStackTrace(), stackTraceString(innermost));
            }
        }

        if (timers != null) {
            for (var entry : timers.entrySet()) {
                TimedScope scope = entry.getValue();
                if (!scope.isRunning()) {
                    out.put(naming.timeElapsed(entry.getKey()), round1(scope.elapsedMilliseconds()));
                }
            }
        }

        if (counters != null) {
            for (var entry : counters.entrySet()) {
                out.put(naming.count(entry.getKey()), entry.getValue());
            }
        }

        for (var entry : fields.entrySet()) {
            if (suppressedFields != null && suppressedFields.contains(entry.getKey())) continue;
            out.put(entry.getKey(), entry.getValue());
        }

        cfg.emit(new EventEmission(startTimestamp, level, out));
    }

    public interface TimedScope extends AutoCloseable {
        @Override
        void close();
        double elapsedMilliseconds();
        boolean isRunning();
    }

    private final class TimedScopeImpl implements TimedScope {
        private final long timerStartNanos = System.nanoTime();
        private long elapsedNanos = -1L;

        @Override
        public void close() {
            if (elapsedNanos >= 0L) return;
            elapsedNanos = System.nanoTime() - timerStartNanos;
        }

        @Override
        public double elapsedMilliseconds() {
            long e = elapsedNanos >= 0L ? elapsedNanos : (System.nanoTime() - timerStartNanos);
            return e / 1_000_000.0;
        }

        @Override
        public boolean isRunning() {
            return elapsedNanos < 0L;
        }
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
