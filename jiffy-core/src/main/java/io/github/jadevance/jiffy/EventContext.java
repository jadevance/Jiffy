package io.github.jadevance.jiffy;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class EventContext implements AutoCloseable {

    private static final String DEFAULT_EXCEPTION_PREFIX = "Exception";

    private final long startNanos = System.nanoTime();
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Configuration config;

    private Map<String, Object> privateData;
    private Map<String, TimedScope> timers;
    private Map<String, Long> counters;
    private Set<String> suppressedFields;
    private List<CapturedException> capturedExceptions;

    private Instant startTimestamp = Instant.now();
    private String component;
    private String operation;
    private Level level = Level.INFO;
    private String reason;
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

    public EventContext setComponent(String component) {
        this.component = component;
        return this;
    }

    public EventContext setOperation(String operation) {
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

    public Object get(String key) {
        return fields.get(key);
    }

    public EventContext trySet(String key, Supplier<Object> valueFn) {
        return trySet(key, valueFn, FieldConflict.OVERWRITE);
    }

    public EventContext trySet(String key, Supplier<Object> valueFn, FieldConflict behavior) {
        try {
            return set(key, valueFn.get(), behavior);
        } catch (Throwable ignored) {
            return this;
        }
    }

    public EventContext addValues(Map<String, Object> values) {
        if (values == null) return this;
        for (var e : values.entrySet()) {
            set(e.getKey(), e.getValue());
        }
        return this;
    }

    public EventContext includeStructure(Object structure) {
        return includeStructure(structure, null, false);
    }

    public EventContext includeStructure(Object structure, String keyPrefix) {
        return includeStructure(structure, keyPrefix, false);
    }

    public EventContext includeStructure(Object structure, String keyPrefix, boolean includeNullValues) {
        if (structure == null) return this;
        Class<?> clazz = structure.getClass();
        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                try {
                    Object value = rc.getAccessor().invoke(structure);
                    if (value == null && !includeNullValues) continue;
                    set(composeStructureKey(keyPrefix, rc.getName()), value);
                } catch (ReflectiveOperationException ignored) {}
            }
            return this;
        }
        try {
            BeanInfo info = Introspector.getBeanInfo(clazz, Object.class);
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                Method reader = pd.getReadMethod();
                if (reader == null) continue;
                try {
                    Object value = reader.invoke(structure);
                    if (value == null && !includeNullValues) continue;
                    set(composeStructureKey(keyPrefix, pd.getName()), value);
                } catch (ReflectiveOperationException ignored) {}
            }
        } catch (IntrospectionException ignored) {}
        return this;
    }

    private static String composeStructureKey(String prefix, String name) {
        return (prefix == null || prefix.isEmpty()) ? name : prefix + "_" + name;
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
        return includeException(t, null);
    }

    public EventContext includeException(Throwable t, String keyPrefix) {
        setToError("An exception has occurred");
        if (capturedExceptions == null) capturedExceptions = new ArrayList<>();
        capturedExceptions.add(new CapturedException(t, keyPrefix));
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

    public EventContext setToWarning() {
        return setToWarning(null);
    }

    public EventContext setToWarning(String reason) {
        this.level = Level.WARNING;
        this.reason = reason;
        return this;
    }

    public EventContext setToError() {
        return setToError(null);
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

        Configuration cfg = config != null ? config : Configuration.active();
        cfg.invokeBeforeLogging(this);

        if (suppressed) return;

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

        if (capturedExceptions != null) {
            for (CapturedException ex : capturedExceptions) {
                emitException(out, naming, ex.throwable(), ex.keyPrefix());
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

    private static void emitException(Map<String, Object> out, NamingConvention naming, Throwable t, String customPrefix) {
        String typeKey, msgKey, stackKey, innerTypeKey, innerMsgKey, innerStackKey;
        if (customPrefix == null) {
            typeKey = naming.exceptionType();
            msgKey = naming.exceptionMessage();
            stackKey = naming.exceptionStackTrace();
            innerTypeKey = naming.innermostExceptionType();
            innerMsgKey = naming.innermostExceptionMessage();
            innerStackKey = naming.innermostExceptionStackTrace();
        } else {
            typeKey = customPrefix + "_Type";
            msgKey = customPrefix + "_Message";
            stackKey = customPrefix + "_StackTrace";
            innerTypeKey = "Innermost" + customPrefix + "_Type";
            innerMsgKey = "Innermost" + customPrefix + "_Message";
            innerStackKey = "Innermost" + customPrefix + "_StackTrace";
        }
        out.put(typeKey, t.getClass().getName());
        out.put(msgKey, String.valueOf(t.getMessage()));
        out.put(stackKey, stackTraceString(t));
        Throwable innermost = t;
        while (innermost.getCause() != null && innermost.getCause() != innermost) {
            innermost = innermost.getCause();
        }
        if (innermost != t) {
            out.put(innerTypeKey, innermost.getClass().getName());
            out.put(innerMsgKey, String.valueOf(innermost.getMessage()));
            out.put(innerStackKey, stackTraceString(innermost));
        }
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

    private record CapturedException(Throwable throwable, String keyPrefix) {}

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String stackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
