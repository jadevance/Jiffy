package io.github.jadevance.jiffy;

public final class FieldName {

    public static final String APPLICATION = "Application";
    public static final String LEVEL = "Level";
    public static final String COMPONENT = "Component";
    public static final String OPERATION = "Operation";
    public static final String TIME_ELAPSED = "TimeElapsed";

    public static final String ERROR_REASON = "ErrorReason";
    public static final String WARNING_REASON = "WarningReason";

    public static final String EXCEPTION_TYPE = "Exception_Type";
    public static final String EXCEPTION_MESSAGE = "Exception_Message";
    public static final String EXCEPTION_STACK_TRACE = "Exception_StackTrace";
    public static final String INNERMOST_EXCEPTION_TYPE = "InnermostException_Type";
    public static final String INNERMOST_EXCEPTION_MESSAGE = "InnermostException_Message";
    public static final String INNERMOST_EXCEPTION_STACK_TRACE = "InnermostException_StackTrace";

    public static final String TIME_ELAPSED_PREFIX = "TimeElapsed_";
    public static final String COUNT_PREFIX = "Count_";

    private FieldName() {}

    public static String timeElapsed(String key) {
        return TIME_ELAPSED_PREFIX + key;
    }

    public static String count(String key) {
        return COUNT_PREFIX + key;
    }
}
