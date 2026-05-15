package io.github.jadevance.jiffy;

import java.time.Instant;
import java.util.Map;

public record EventEmission(
    Instant timestamp,
    Level level,
    Map<String, Object> fields
) {}
