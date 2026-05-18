package io.github.jadevance.jiffy;

import io.github.jadevance.jiffy.sinks.ConsoleSink;
import io.github.jadevance.jiffy.sinks.Slf4jSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class Configuration {

    private static volatile Configuration active = new Configuration(
        List.of(new Slf4jSink()), NamingConvention.SPIFFY, List.of());

    private final List<Sink> sinks;
    private final NamingConvention naming;
    private final List<Consumer<EventContext>> beforeLogging;

    private Configuration(List<Sink> sinks, NamingConvention naming, List<Consumer<EventContext>> beforeLogging) {
        this.sinks = List.copyOf(sinks);
        this.naming = naming;
        this.beforeLogging = List.copyOf(beforeLogging);
    }

    public static void initialize(Consumer<Builder> setup) {
        Builder b = new Builder();
        setup.accept(b);
        active = b.build();
    }

    public static Configuration active() {
        return active;
    }

    public NamingConvention naming() {
        return naming;
    }

    void invokeBeforeLogging(EventContext ctx) {
        for (Consumer<EventContext> cb : beforeLogging) {
            try {
                cb.accept(ctx);
            } catch (Throwable ignored) {
                // a misbehaving callback must not break the emitting code path
            }
        }
    }

    void emit(EventEmission event) {
        for (Sink s : sinks) {
            try {
                s.emit(event);
            } catch (Throwable ignored) {
                // a misbehaving sink must not break the emitting code path
            }
        }
    }

    public static final class Builder {
        private final Providers providers = new Providers();
        private final Callbacks callbacks = new Callbacks();
        private NamingConvention naming = NamingConvention.SPIFFY;

        public Providers providers() {
            return providers;
        }

        public Callbacks callbacks() {
            return callbacks;
        }

        public Builder naming(NamingConvention naming) {
            this.naming = Objects.requireNonNull(naming, "naming");
            return this;
        }

        Configuration build() {
            return new Configuration(providers.sinks, naming, callbacks.beforeLogging);
        }
    }

    public static final class Providers {
        private final List<Sink> sinks = new ArrayList<>();

        public Providers console() {
            sinks.add(new ConsoleSink());
            return this;
        }

        public Providers slf4j() {
            sinks.add(new Slf4jSink());
            return this;
        }

        public Providers add(Sink sink) {
            sinks.add(sink);
            return this;
        }
    }

    public static final class Callbacks {
        private final List<Consumer<EventContext>> beforeLogging = new ArrayList<>();

        public Callbacks beforeLogging(Consumer<EventContext> callback) {
            beforeLogging.add(Objects.requireNonNull(callback, "callback"));
            return this;
        }
    }
}
