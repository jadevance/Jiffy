package io.github.jadevance.jiffy.sinks;

import io.github.jadevance.jiffy.EventEmission;
import io.github.jadevance.jiffy.Sink;
import io.github.jadevance.jiffy.format.KeyValueFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4jSink implements Sink {

    private static final Logger LOG = LoggerFactory.getLogger("jiffy");

    @Override
    public void emit(EventEmission event) {
        String line = KeyValueFormatter.format(event.fields());
        switch (event.level()) {
            case ERROR   -> LOG.error(line);
            case WARNING -> LOG.warn(line);
            case INFO    -> LOG.info(line);
        }
    }
}
