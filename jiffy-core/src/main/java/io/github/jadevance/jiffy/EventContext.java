package io.github.jadevance.jiffy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class EventContext implements AutoCloseable {

    private final long startNanos = System.nanoTime();
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, Object> privateData = new LinkedHashMap<>();
    private final Map<String, TimedScope> timers = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Set<String> suppressedFields = new HashSet<>();
    private final Configuration config;

    private Instant startTimestamp = Instant.now();
    private String component;
    private String operation;
    private Level level = Level.INFO;
    private String errorReason;
    private String warningReason;
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
        TimedScopeImpl scope = new TimedScopeImpl(key);
        timers.put(key, scope);
        return scope;
    }

    public Map<String, TimedScope> timers() {
        return Collections.unmodifiableMap(timers);
    }

    public EventContext count(String key) {
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
        privateData.put(key, value);
        return this;
    }

    public Object getPrivate(String key) {
        return privateData.get(key);
    }

    public boolean containsPrivate(String key) {
        return privateData.containsKey(key);
    }

    public Map<String, Object> privateData() {
        return Collections.unmodifiableMap(privateData);
    }

    public EventContext setCustomTimestamp(Instant timestamp) {
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
        this.startTimestamp = timestamp;
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

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setToInfo() {
        this.level = Level.INFO;
        this.errorReason = null;
        this.warningReason = null;
    }

    public void setToWarning(String reason) {
        this.level = Level.WARNING;
        this.warningReason = reason;
        this.errorReason = null;
    }

    public void setToError(String reason) {
        this.level = Level.ERROR;
        this.errorReason = reason;
        this.warningReason = null;
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

        Configuration cfg = config != null ? config : Configuration.active();
        NamingConvention naming = cfg.naming();

        Map<String, Object> out = new LinkedHashMap<>(GlobalEventContext.instance().snapshot());
        out.put(naming.level(), level.pretty());
        if (component != null) out.put(naming.component(), component);
        if (operation != null) out.put(naming.operation(), operation);
        out.put(naming.timeElapsed(), round1(elapsedMilliseconds()));

        if (errorReason != null) out.put(naming.errorReason(), errorReason);
        if (warningReason != null) out.put(naming.warningReason(), warningReason);

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

        for (var entry : timers.entrySet()) {
            TimedScopeImpl scope = (TimedScopeImpl) entry.getValue();
            if (!scope.isRunning()) {
                out.put(naming.timeElapsed(scope.key()), round1(scope.elapsedMsAtClose()));
            }
        }

        for (var entry : counters.entrySet()) {
            out.put(naming.count(entry.getKey()), entry.getValue());
        }

        for (var entry : fields.entrySet()) {
            if (suppressedFields.contains(entry.getKey())) continue;
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
        private final String key;
        private final long timerStartNanos = System.nanoTime();
        private long elapsedNanos = -1L;

        TimedScopeImpl(String key) {
            this.key = key;
        }

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

        String key() {
            return key;
        }

        double elapsedMsAtClose() {
            return elapsedNanos < 0L ? 0.0 : elapsedNanos / 1_000_000.0;
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
