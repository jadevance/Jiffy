package io.github.jadevance.jiffy;

@FunctionalInterface
public interface Sink {
    void emit(EventEmission event);
}
