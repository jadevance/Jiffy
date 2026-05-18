package io.github.jadevance.jiffy;

public interface NamingConvention {

    String application();
    String level();
    String component();
    String operation();
    String timeElapsed();

    String errorReason();
    String warningReason();

    String exceptionType();
    String exceptionMessage();
    String exceptionStackTrace();
    String innermostExceptionType();
    String innermostExceptionMessage();
    String innermostExceptionStackTrace();

    String timeElapsed(String key);
    String count(String key);

    NamingConvention SPIFFY = new SpiffyNamingConvention();
    NamingConvention JAVA = new JavaNamingConvention();
}
