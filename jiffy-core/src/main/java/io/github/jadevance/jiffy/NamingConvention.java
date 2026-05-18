package io.github.jadevance.jiffy;

public interface NamingConvention {

    String level();
    String component();
    String operation();
    String timeElapsed();

    String errorReason();
    String warningReason();

    String timeElapsed(String key);
    String count(String key);

    default String exceptionType() { return "Exception_Type"; }
    default String exceptionMessage() { return "Exception_Message"; }
    default String exceptionStackTrace() { return "Exception_StackTrace"; }
    default String innermostExceptionType() { return "InnermostException_Type"; }
    default String innermostExceptionMessage() { return "InnermostException_Message"; }
    default String innermostExceptionStackTrace() { return "InnermostException_StackTrace"; }

    NamingConvention SPIFFY = new SpiffyNamingConvention();
    NamingConvention LEGACY = new LegacyNamingConvention();
    NamingConvention JAVA = new JavaNamingConvention();
}
