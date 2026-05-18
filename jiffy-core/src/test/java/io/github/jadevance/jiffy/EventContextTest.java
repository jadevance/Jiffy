package io.github.jadevance.jiffy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
        assertEquals("UserService", fields.get("c"));
        assertEquals("CreateUser", fields.get("o"));
        assertEquals(42, fields.get("UserId"));
        assertEquals("Info", fields.get("l"));
        assertNotNull(fields.get("ms"));
    }

    @Test
    void timeRecordsSubOperationDuration() throws Exception {
        try (var ctx = new EventContext("C", "Op")) {
            try (var t = ctx.time("Inner")) {
                Thread.sleep(5);
            }
        }
        var fields = captured.get(0).fields();
        assertNotNull(fields.get("ms_Inner"));
        assertTrue(((Number) fields.get("ms_Inner")).doubleValue() >= 4.0);
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
        assertEquals("Error", fields.get("l"));
        assertEquals("An exception has occurred", fields.get("msg"));
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

    @Test
    void privateDataIsAccessibleDuringEventButNotEmitted() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.setPrivate("InternalId", "abc-123");
            ctx.set("Public", "ok");

            assertEquals("abc-123", ctx.getPrivate("InternalId"));
            assertTrue(ctx.containsPrivate("InternalId"));
            assertFalse(ctx.containsPrivate("Missing"));
            assertEquals(1, ctx.privateData().size());
        }

        var fields = captured.get(0).fields();
        assertEquals("ok", fields.get("Public"));
        assertFalse(fields.containsKey("InternalId"), "private data must not be emitted");
    }

    @Test
    void customTimestampOverridesEventTimestamp() {
        Instant custom = Instant.parse("2020-01-15T12:00:00Z");
        try (var ctx = new EventContext("C", "Op")) {
            ctx.setCustomTimestamp(custom);
        }
        assertEquals(custom, captured.get(0).timestamp());
    }

    @Test
    void customTimestampRejectsNull() {
        try (var ctx = new EventContext("C", "Op")) {
            assertThrows(IllegalArgumentException.class, () -> ctx.setCustomTimestamp(null));
        }
    }

    @Test
    void timersCollectionExposesInFlightAndCompletedTimers() throws Exception {
        try (var ctx = new EventContext("C", "Op")) {
            var outer = ctx.time("Outer");
            try (var inner = ctx.time("Inner")) {
                Thread.sleep(2);
                assertTrue(inner.isRunning());
                assertTrue(inner.elapsedMilliseconds() >= 0.0);

                var snapshot = ctx.timers();
                assertEquals(2, snapshot.size());
                assertTrue(snapshot.containsKey("Outer"));
                assertTrue(snapshot.containsKey("Inner"));
            }
            assertFalse(ctx.timers().get("Inner").isRunning(), "closed timer should report not running");
            assertTrue(ctx.timers().get("Outer").isRunning(), "unclosed timer should still be running");
            outer.close();
        }
        var fields = captured.get(0).fields();
        assertNotNull(fields.get("ms_Outer"));
        assertNotNull(fields.get("ms_Inner"));
    }

    @Test
    void timersMapIsUnmodifiable() {
        try (var ctx = new EventContext("C", "Op")) {
            try (var t = ctx.time("X")) { /* no-op */ }
            assertThrows(UnsupportedOperationException.class, () -> ctx.timers().clear());
        }
    }

    @Test
    void defaultConventionIsSpiffyShort() {
        try (var ctx = new EventContext("UserSvc", "Op")) { /* no-op */ }
        var fields = captured.get(0).fields();
        assertTrue(fields.containsKey("l"));
        assertTrue(fields.containsKey("c"));
        assertTrue(fields.containsKey("o"));
        assertTrue(fields.containsKey("ms"));

        assertFalse(fields.containsKey("Level"));
        assertFalse(fields.containsKey("Component"));
    }

    @Test
    void legacyConventionEmitsPascalCaseStandardFields() {
        Configuration.initialize(c -> c
            .naming(NamingConvention.LEGACY)
            .providers().add(captured::add));

        try (var ctx = new EventContext("UserSvc", "Op")) {
            ctx.setToError("bad");
            ctx.count("Hits");
            try (var t = ctx.time("DbInsert")) { /* no-op */ }
        }

        var fields = captured.get(0).fields();
        assertTrue(fields.containsKey("Level"));
        assertTrue(fields.containsKey("Component"));
        assertTrue(fields.containsKey("Operation"));
        assertTrue(fields.containsKey("TimeElapsed"));
        assertTrue(fields.containsKey("ErrorReason"));
        assertTrue(fields.containsKey("Count_Hits"));
        assertTrue(fields.containsKey("TimeElapsed_DbInsert"));
        assertFalse(fields.containsKey("WarningReason"));

        assertFalse(fields.containsKey("l"));
        assertFalse(fields.containsKey("msg"));
        assertFalse(fields.containsKey("ms"));
        assertFalse(fields.containsKey("ms_DbInsert"));
    }

    @Test
    void spiffyConventionCollapsesErrorAndWarningReasonToMsg() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.setToWarning("first warn");
            ctx.setToError("now an error");
        }

        var fields = captured.get(0).fields();
        assertEquals("Error", fields.get("l"));
        assertEquals("now an error", fields.get("msg"));
        assertFalse(fields.containsKey("WarningReason"));
        assertFalse(fields.containsKey("ErrorReason"));
    }

    @Test
    void setToInfoClearsErrorAndWarningReason() {
        try (var ctx = new EventContext("C", "Op")) {
            ctx.setToError("oops");
            ctx.setToInfo();
        }

        var fields = captured.get(0).fields();
        assertEquals("Info", fields.get("l"));
        assertFalse(fields.containsKey("msg"));
    }

    @Test
    void javaConventionEmitsCamelCaseStandardFields() {
        Configuration.initialize(c -> c
            .naming(NamingConvention.JAVA)
            .providers().add(captured::add));

        try (var ctx = new EventContext("UserSvc", "Op")) {
            ctx.setToError("bad");
            ctx.count("Hits");
            try (var t = ctx.time("DbInsert")) { /* no-op */ }
        }

        var fields = captured.get(0).fields();
        assertTrue(fields.containsKey("level"));
        assertTrue(fields.containsKey("component"));
        assertTrue(fields.containsKey("operation"));
        assertTrue(fields.containsKey("timeElapsed"));
        assertTrue(fields.containsKey("message"));
        assertTrue(fields.containsKey("count_Hits"));
        assertTrue(fields.containsKey("timeElapsed_DbInsert"));

        assertFalse(fields.containsKey("Level"));
        assertFalse(fields.containsKey("Component"));
        assertFalse(fields.containsKey("ErrorReason"));
        assertFalse(fields.containsKey("Count_Hits"));
        assertFalse(fields.containsKey("msg"));
    }

    @Test
    void userSetFieldsPassThroughVerbatimUnderAnyConvention() {
        Configuration.initialize(c -> c
            .naming(NamingConvention.JAVA)
            .providers().add(captured::add));

        try (var ctx = new EventContext("C", "Op")) {
            ctx.set("UserId", 42);
            ctx.set("user_role", "admin");
            ctx.set("Component", "userOverride");
        }

        var fields = captured.get(0).fields();
        assertEquals(42, fields.get("UserId"));
        assertEquals("admin", fields.get("user_role"));
        assertEquals("userOverride", fields.get("Component"));
        assertEquals("C", fields.get("component"), "library-emitted component uses Java convention");
    }

    @Test
    void javaConventionUsesCamelCaseForExceptionFields() {
        Configuration.initialize(c -> c
            .naming(NamingConvention.JAVA)
            .providers().add(captured::add));

        try (var ctx = new EventContext("C", "Op")) {
            try {
                try {
                    throw new IllegalStateException("inner");
                } catch (IllegalStateException e) {
                    throw new RuntimeException("outer", e);
                }
            } catch (RuntimeException e) {
                ctx.includeException(e);
            }
        }

        var fields = captured.get(0).fields();
        assertEquals("Error", fields.get("level"));
        assertEquals("An exception has occurred", fields.get("message"));
        assertEquals(RuntimeException.class.getName(), fields.get("exceptionType"));
        assertEquals("outer", fields.get("exceptionMessage"));
        assertEquals(IllegalStateException.class.getName(), fields.get("innermostExceptionType"));
        assertEquals("inner", fields.get("innermostExceptionMessage"));

        assertFalse(fields.containsKey("Exception_Type"));
        assertFalse(fields.containsKey("InnermostException_Type"));
    }
}
