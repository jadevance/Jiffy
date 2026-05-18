package io.github.jadevance.jiffy;

final class JavaNamingConvention implements NamingConvention {

    @Override public String level() { return "level"; }
    @Override public String component() { return "component"; }
    @Override public String operation() { return "operation"; }
    @Override public String timeElapsed() { return "timeElapsed"; }

    @Override public String errorReason() { return "message"; }
    @Override public String warningReason() { return "message"; }

    @Override public String exceptionType() { return "exceptionType"; }
    @Override public String exceptionMessage() { return "exceptionMessage"; }
    @Override public String exceptionStackTrace() { return "exceptionStackTrace"; }
    @Override public String innermostExceptionType() { return "innermostExceptionType"; }
    @Override public String innermostExceptionMessage() { return "innermostExceptionMessage"; }
    @Override public String innermostExceptionStackTrace() { return "innermostExceptionStackTrace"; }

    @Override public String timeElapsed(String key) { return "timeElapsed_" + key; }
    @Override public String count(String key) { return "count_" + key; }
}
