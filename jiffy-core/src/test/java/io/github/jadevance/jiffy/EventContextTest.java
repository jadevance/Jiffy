package io.github.jadevance.jiffy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class EventContextTest {

    private List<EventEmission> captured;

    @BeforeEach
    void setUp() {
        captured = new CopyOnWriteArrayList<>();
        Configuration.initialize(c -> c.providers().add(captured::add));
    }

    @Test
    void emitsOnceOnClose() {
        try (var ctx = new EventContext("UserService", "CreateUser")) {
            ctx.set("UserId", 42);
        }

        assertEquals(1, captured.size());
        var fields = captured.get(0).fields();
        assertEquals("UserService", fields.get("Component"));
        assertEquals("CreateUser", fields.get("Operation"));
        assertEquals(42, fields.get("UserId"));
        assertEquals("Info", fields.get("Level"));
        assertNotNull(fields.get("TimeElapsed"));
    }

    @Test
    void timeRecordsSubOperationDuration() throws Exception {
        try (var ctx = new EventContext("C", "Op")) {
            try (var t = ctx.time("Inner")) {
                Thread.sleep(5);
            }
        }
        var fields = captured.get(0).fields();
        assertNotNull(fields.get("TimeElapsed_Inner"));
        assertTrue(((Number) fields.get("TimeElapsed_Inner")).doubleValue() >= 4.0);
    }

    @Test
    void includeExceptionEscalatesLevelAndAddsFields() {
        try (var ctx = new EventContext("C", "Op")) {
            try {
                throw new RuntimeException("boom");
            } catch (RuntimeException e) {
                ctx.includeException(e);
            }
        }
        var fields = captured.get(0).fields();
        assertEquals("Error", fields.get("Level"));
        assertEquals("An exception has occurred", fields.get("ErrorReason"));
        assertEquals(RuntimeException.class.getName(), fields.get("Exception_Type"));
        assertEquals("boom", fields.get("Exception_Message"));
        assertNotNull(fields.get("Exception_StackTrace"));
    }

    @Test
    void innermostExceptionIsCapturedWhenNested() {
        try (var ctx = new EventContext("C", "Op")) {
            try {
                try {
                    throw new IllegalStateException("root cause");
                } catch (IllegalStateException e) {
                    throw new RuntimeException("wrapped", e);
                }
            } catch (RuntimeException e) {
                ctx.includeException(e);
            }
        }
        var fields = captured.get(0).fields();
        assertEquals(IllegalStateException.class.getName(), fields.get("InnermostException_Type"));
        assertEquals("root cause", fields.get("InnermostException_Message"));
    }

    @Test
    void suppressedEventDoesNotEmit() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.suppress();
        }
        assertEquals(0, captured.size());
    }

    @Test
    void suppressedFieldsAreOmittedFromOutput() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.set("Public", "ok");
            ctx.set("Secret", "shhh");
            ctx.suppressFields("Secret");
        }
        var fields = captured.get(0).fields();
        assertEquals("ok", fields.get("Public"));
        assertFalse(fields.containsKey("Secret"));
    }

    @Test
    void fieldConflictAppendConcatenates() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.set("Tag", "first");
            ctx.set("Tag", "second", FieldConflict.APPEND);
        }
        assertEquals("first,second", captured.get(0).fields().get("Tag"));
    }

    @Test
    void fieldConflictIgnoreKeepsOriginal() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.set("Tag", "first");
            ctx.set("Tag", "second", FieldConflict.IGNORE);
        }
        assertEquals("first", captured.get(0).fields().get("Tag"));
    }

    @Test
    void countIncrements() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.count("Hits");
            ctx.count("Hits");
            ctx.count("Hits");
        }
        assertEquals(3L, captured.get(0).fields().get("Count_Hits"));
    }

    @Test
    void globalFieldsAppearInOutput() {
        GlobalEventContext.instance().set("Application", "test-app");
        try (var ctx = new EventContext("C", "Op")) {
            ctx.set("X", 1);
        }
        var fields = captured.get(0).fields();
        assertEquals("test-app", fields.get("Application"));
        var keys = fields.keySet().iterator();
        assertEquals("Application", keys.next(), "global fields must appear before standard fields");
    }
}
