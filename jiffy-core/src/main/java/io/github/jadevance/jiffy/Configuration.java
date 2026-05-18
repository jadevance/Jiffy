package io.github.jadevance.jiffy;

import io.github.jadevance.jiffy.sinks.ConsoleSink;
import io.github.jadevance.jiffy.sinks.Slf4jSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class Configuration {

    private static volatile Configuration active = new Configuration(List.of(new Slf4jSink()), NamingConvention.SPIFFY);

    private final List<Sink> sinks;
    private final NamingConvention naming;

    private Configuration(List<Sink> sinks, NamingConvention naming) {
        this.sinks = List.copyOf(sinks);
        this.naming = naming;
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
        private NamingConvention naming = NamingConvention.SPIFFY;

        public Providers providers() {
            return providers;
        }

        public Builder naming(NamingConvention naming) {
            this.naming = Objects.requireNonNull(naming, "naming");
            return this;
        }

        Configuration build() {
            return new Configuration(providers.sinks, naming);
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
