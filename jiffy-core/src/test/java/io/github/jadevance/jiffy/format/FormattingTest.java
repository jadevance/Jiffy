package io.github.jadevance.jiffy.format;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormattingTest {

    @Test
    void customTimestampFormatIsApplied() {
        var f = new Formatting().timestamp("yyyy/MM/dd");
        Instant ts = Instant.parse("2026-05-18T12:00:00Z");
        Map<String, Object> fields = Map.of("k", "v");
        assertTrue(KeyValueFormatter.format(ts, fields, f).startsWith("[2026/05/18] "));
    }

    @Test
    void nullValueIsRenderedAsConfiguredString() {
        var f = new Formatting().nullValue("(null)");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("k", null);
        assertEquals("k=(null)", KeyValueFormatter.format(fields, f));
    }

    @Test
    void nullValueDefaultIsEmptyString() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("k", null);
        assertEquals("k=\"\"", KeyValueFormatter.format(fields, Formatting.DEFAULT));
    }

    @Test
    void newlinesReplaceWithSpaceFlattensMultilineValues() {
        var f = new Formatting().newlines(NewlineFormatting.REPLACE_WITH_SPACE);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("trace", "line1\nline2\rline3");
        String out = KeyValueFormatter.format(fields, f);
        assertEquals("trace=\"line1 line2 line3\"", out);
    }

    @Test
    void newlinesEscapeProducesLiteralBackslashN() {
        var f = new Formatting().newlines(NewlineFormatting.ESCAPE);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("trace", "line1\nline2");
        assertEquals("trace=\"line1\\nline2\"", KeyValueFormatter.format(fields, f));
    }

    @Test
    void newlinesPreserveKeepsRawCharacters() {
        var f = new Formatting().newlines(NewlineFormatting.PRESERVE);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("trace", "line1\nline2");
        String out = KeyValueFormatter.format(fields, f);
        assertTrue(out.contains("line1\nline2"), "expected raw newline preserved");
    }

    @Test
    void specialValueKeepSkipsQuotingEvenWithSpaces() {
        var f = new Formatting().specialValue(SpecialValueFormatting.KEEP);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("msg", "hello world");
        assertEquals("msg=hello world", KeyValueFormatter.format(fields, f));
    }

    @Test
    void deprioritizeValueLengthMovesLongValuesToEnd() {
        var f = new Formatting().deprioritizeValueLength(20);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("LongStack", "x".repeat(50));
        fields.put("Short1", "a");
        fields.put("Short2", "b");

        String out = KeyValueFormatter.format(fields, f);
        int short1Index = out.indexOf("Short1=");
        int short2Index = out.indexOf("Short2=");
        int longIndex = out.indexOf("LongStack=");
        assertTrue(short1Index < longIndex, "short fields must appear before long ones");
        assertTrue(short2Index < longIndex, "short fields must appear before long ones");
    }

    @Test
    void deprioritizeDefaultLeavesOrderUnchanged() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("LongStack", "x".repeat(50));
        fields.put("Short", "a");

        String out = KeyValueFormatter.format(fields, Formatting.DEFAULT);
        assertTrue(out.indexOf("LongStack=") < out.indexOf("Short="),
            "default formatting preserves insertion order");
    }
}
