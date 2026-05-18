package io.github.jadevance.jiffy.format;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class Formatting {

    public static final String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS'Z'";
    public static final Formatting DEFAULT = new Formatting();

    private String timestampFormat = DEFAULT_TIMESTAMP_FORMAT;
    private DateTimeFormatter timestampFormatter = DateTimeFormatter
        .ofPattern(DEFAULT_TIMESTAMP_FORMAT)
        .withZone(ZoneOffset.UTC);
    private String nullValue = "";
    private NewlineFormatting newlines = NewlineFormatting.ESCAPE;
    private SpecialValueFormatting specialValue = SpecialValueFormatting.QUOTE;
    private int deprioritizeValueLength = Integer.MAX_VALUE;

    public Formatting timestamp(String formatString) {
        this.timestampFormat = Objects.requireNonNull(formatString, "formatString");
        this.timestampFormatter = DateTimeFormatter.ofPattern(formatString).withZone(ZoneOffset.UTC);
        return this;
    }

    public Formatting nullValue(String value) {
        this.nullValue = Objects.requireNonNull(value, "value");
        return this;
    }

    public Formatting newlines(NewlineFormatting newlines) {
        this.newlines = Objects.requireNonNull(newlines, "newlines");
        return this;
    }

    public Formatting specialValue(SpecialValueFormatting specialValue) {
        this.specialValue = Objects.requireNonNull(specialValue, "specialValue");
        return this;
    }

    public Formatting deprioritizeValueLength(int length) {
        this.deprioritizeValueLength = length;
        return this;
    }

    public String timestampFormat() { return timestampFormat; }
    public DateTimeFormatter timestampFormatter() { return timestampFormatter; }
    public String nullValueText() { return nullValue; }
    public NewlineFormatting newlineHandling() { return newlines; }
    public SpecialValueFormatting specialValueHandling() { return specialValue; }
    public int deprioritizeValueLengthThreshold() { return deprioritizeValueLength; }
}
