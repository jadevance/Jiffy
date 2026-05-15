package io.github.jadevance.jiffy;

public enum Level {
    INFO("Info"),
    WARNING("Warning"),
    ERROR("Error");

    private final String pretty;

    Level(String pretty) {
        this.pretty = pretty;
    }

    public String pretty() {
        return pretty;
    }
}
