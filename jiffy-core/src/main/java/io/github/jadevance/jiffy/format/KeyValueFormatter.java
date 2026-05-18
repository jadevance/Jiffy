package io.github.jadevance.jiffy.format;

import java.time.Instant;
import java.util.Map;

public final class KeyValueFormatter {

    private KeyValueFormatter() {}

    public static String format(Map<String, Object> fields) {
        return format(fields, Formatting.DEFAULT);
    }

    public static String format(Instant timestamp, Map<String, Object> fields) {
        return format(timestamp, fields, Formatting.DEFAULT);
    }

    public static String format(Map<String, Object> fields, Formatting f) {
        StringBuilder sb = new StringBuilder();
        appendFields(sb, fields, f);
        return sb.toString();
    }

    public static String format(Instant timestamp, Map<String, Object> fields, Formatting f) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(f.timestampFormatter().format(timestamp)).append("] ");
        appendFields(sb, fields, f);
        return sb.toString();
    }

    private static void appendFields(StringBuilder sb, Map<String, Object> fields, Formatting f) {
        int threshold = f.deprioritizeValueLengthThreshold();
        boolean[] first = {true};
        for (var e : fields.entrySet()) {
            if (valueLength(e.getValue(), f) > threshold) continue;
            appendField(sb, first, e.getKey(), e.getValue(), f);
        }
        if (threshold < Integer.MAX_VALUE) {
            for (var e : fields.entrySet()) {
                if (valueLength(e.getValue(), f) <= threshold) continue;
                appendField(sb, first, e.getKey(), e.getValue(), f);
            }
        }
    }

    private static void appendField(StringBuilder sb, boolean[] first, String key, Object value, Formatting f) {
        if (!first[0]) sb.append(' ');
        first[0] = false;
        sb.append(key).append('=').append(formatValue(value, f));
    }

    private static int valueLength(Object v, Formatting f) {
        if (v == null) return f.nullValueText().length();
        return v.toString().length();
    }

    private static String formatValue(Object v, Formatting f) {
        if (v == null) return formatString(f.nullValueText(), f);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return formatString(applyNewlinePolicy(v.toString(), f), f);
    }

    private static String formatString(String s, Formatting f) {
        if (f.specialValueHandling() == SpecialValueFormatting.KEEP) return s;
        if (s.isEmpty() || needsQuoting(s, f)) return quote(s, f);
        return s;
    }

    private static String applyNewlinePolicy(String s, Formatting f) {
        return switch (f.newlineHandling()) {
            case PRESERVE -> s;
            case ESCAPE -> s;
            case REPLACE_WITH_SPACE -> s.replace('\n', ' ').replace('\r', ' ');
        };
    }

    private static String quote(String s, Formatting f) {
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append(f.newlineHandling() == NewlineFormatting.PRESERVE ? "\n" : "\\n");
                case '\r' -> out.append(f.newlineHandling() == NewlineFormatting.PRESERVE ? "\r" : "\\r");
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    private static boolean needsQuoting(String s, Formatting f) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '"' || c == '=' || c == '\\') return true;
            if (c < 0x20 && f.newlineHandling() != NewlineFormatting.PRESERVE) return true;
        }
        return false;
    }
}
