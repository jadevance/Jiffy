package io.github.jadevance.jiffy;

final class LegacyNamingConvention implements NamingConvention {

    @Override public String level() { return "Level"; }
    @Override public String component() { return "Component"; }
    @Override public String operation() { return "Operation"; }
    @Override public String timeElapsed() { return "TimeElapsed"; }

    @Override public String errorReason() { return "ErrorReason"; }
    @Override public String warningReason() { return "WarningReason"; }

    @Override public String exceptionType() { return "Exception_Type"; }
    @Override public String exceptionMessage() { return "Exception_Message"; }
    @Override public String exceptionStackTrace() { return "Exception_StackTrace"; }
    @Override public String innermostExceptionType() { return "InnermostException_Type"; }
    @Override public String innermostExceptionMessage() { return "InnermostException_Message"; }
    @Override public String innermostExceptionStackTrace() { return "InnermostException_StackTrace"; }

    @Override public String timeElapsed(String key) { return "TimeElapsed_" + key; }
    @Override public String count(String key) { return "Count_" + key; }
}
