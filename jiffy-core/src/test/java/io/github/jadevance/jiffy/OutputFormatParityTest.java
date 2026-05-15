package io.github.jadevance.jiffy;

import io.github.jadevance.jiffy.format.KeyValueFormatter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputFormatParityTest {

    @Test
    void matchesSpiffyReadmeExampleLine() {
        Instant ts = Instant.parse("2014-06-13T00:05:17.634Z");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Application", "MyApplication");
        fields.put("Level", "Info");
        fields.put("Component", "Program");
        fields.put("Operation", "Main");
        fields.put("TimeElapsed", 1004.2);
        fields.put("Key", "Value");

        String actual = KeyValueFormatter.format(ts, fields);

        assertEquals(
            "[2014-06-13 00:05:17.634Z] Application=MyApplication Level=Info Component=Program Operation=Main TimeElapsed=1004.2 Key=Value",
            actual
        );
    }

    @Test
    void valuesWithSpacesAreQuoted() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Message", "hello world");
        assertEquals("Message=\"hello world\"", KeyValueFormatter.format(fields));
    }

    @Test
    void quotesInValuesAreEscaped() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Quoted", "say \"hi\"");
        assertEquals("Quoted=\"say \\\"hi\\\"\"", KeyValueFormatter.format(fields));
    }

    @Test
    void numbersAndBooleansAreUnquoted() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Count", 42);
        fields.put("Elapsed", 12.5);
        fields.put("Ok", true);
        assertEquals("Count=42 Elapsed=12.5 Ok=true", KeyValueFormatter.format(fields));
    }
}
