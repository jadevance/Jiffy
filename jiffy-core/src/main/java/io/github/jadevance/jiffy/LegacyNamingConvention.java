package io.github.jadevance.jiffy;

final class LegacyNamingConvention implements NamingConvention {

    @Override public String level() { return "Level"; }
    @Override public String component() { return "Component"; }
    @Override public String operation() { return "Operation"; }
    @Override public String timeElapsed() { return "TimeElapsed"; }

    @Override public String errorReason() { return "ErrorReason"; }
    @Override public String warningReason() { return "WarningReason"; }

    @Override public String timeElapsed(String key) { return "TimeElapsed_" + key; }
    @Override public String count(String key) { return "Count_" + key; }
}
