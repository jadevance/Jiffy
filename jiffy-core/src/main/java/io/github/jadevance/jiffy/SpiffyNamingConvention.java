package io.github.jadevance.jiffy;

final class SpiffyNamingConvention implements NamingConvention {

    @Override public String level() { return "l"; }
    @Override public String component() { return "c"; }
    @Override public String operation() { return "o"; }
    @Override public String timeElapsed() { return "ms"; }

    @Override public String errorReason() { return "msg"; }
    @Override public String warningReason() { return "msg"; }

    @Override public String timeElapsed(String key) { return "ms_" + key; }
    @Override public String count(String key) { return "Count_" + key; }
}
