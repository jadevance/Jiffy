package io.github.jadevance.jiffy;

import io.github.jadevance.jiffy.sinks.ConsoleSink;
import io.github.jadevance.jiffy.sinks.Slf4jSink;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Configuration {

    private static volatile Configuration active = new Configuration(List.of(new Slf4jSink()));

    private final List<Sink> sinks;

    private Configuration(List<Sink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    public static void initialize(Consumer<Builder> setup) {
        Builder b = new Builder();
        setup.accept(b);
        active = b.build();
    }

    public static Configuration active() {
        return active;
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

        public Providers providers() {
            return providers;
        }

        Configuration build() {
            return new Configuration(providers.sinks);
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
}
