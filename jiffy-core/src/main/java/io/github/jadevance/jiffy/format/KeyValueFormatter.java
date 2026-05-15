package io.github.jadevance.jiffy.format;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class KeyValueFormatter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC);

    private KeyValueFormatter() {}

    public static String format(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) sb.append(' ');
            first = false;
            sb.append(e.getKey()).append('=').append(formatValue(e.getValue()));
        }
        return sb.toString();
    }

    public static String format(Instant timestamp, Map<String, Object> fields) {
        return '[' + TIMESTAMP.format(timestamp) + "] " + format(fields);
    }

    private static String formatValue(Object v) {
        if (v == null) return "";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        String s = v.toString();
        if (s.isEmpty() || needsQuoting(s)) {
            return '"' + s.replace("\\", "\\\\")
                          .replace("\"", "\\\"")
                          .replace("\n", "\\n")
                          .replace("\r", "\\r") + '"';
        }
        return s;
    }

    private static boolean needsQuoting(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '"' || c == '=' || c == '\\' || c < 0x20) return true;
        }
        return false;
    }
}
