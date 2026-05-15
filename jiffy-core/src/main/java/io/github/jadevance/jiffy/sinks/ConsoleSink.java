package io.github.jadevance.jiffy.sinks;

import io.github.jadevance.jiffy.EventEmission;
import io.github.jadevance.jiffy.Sink;
import io.github.jadevance.jiffy.format.KeyValueFormatter;

public final class ConsoleSink implements Sink {
    @Override
    public void emit(EventEmission event) {
        System.out.println(KeyValueFormatter.format(event.timestamp(), event.fields()));
    }
}
