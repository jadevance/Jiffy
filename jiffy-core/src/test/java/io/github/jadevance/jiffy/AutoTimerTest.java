package io.github.jadevance.jiffy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoTimerTest {

    @Test
    void startsRunningOnConstructionWithCountOne() {
        var t = new AutoTimer();
        assertTrue(t.isRunning());
        assertEquals(1, t.count());
    }

    @Test
    void closeStopsTimingAndFreezesElapsed() throws Exception {
        var t = new AutoTimer();
        Thread.sleep(2);
        t.close();
        assertFalse(t.isRunning());
        double frozen = t.elapsedMilliseconds();
        assertTrue(frozen >= 1.0);

        Thread.sleep(5);
        assertEquals(frozen, t.elapsedMilliseconds(), 0.0001);
    }

    @Test
    void resumeIncrementsCountAndContinuesAccumulating() throws Exception {
        var t = new AutoTimer();
        Thread.sleep(2);
        t.close();
        double firstSegment = t.elapsedMilliseconds();

        t.resume();
        assertEquals(2, t.count());
        assertTrue(t.isRunning());

        Thread.sleep(2);
        t.close();
        assertTrue(t.elapsedMilliseconds() > firstSegment);
    }

    @Test
    void resumeOnRunningTimerIsNoOp() {
        var t = new AutoTimer();
        t.resume();
        assertEquals(1, t.count(), "resume on a running timer must not bump count");
    }

    @Test
    void startOverResetsElapsedAndCount() throws Exception {
        var t = new AutoTimer();
        Thread.sleep(2);
        t.close();
        t.resume();
        assertEquals(2, t.count());

        t.startOver();
        assertEquals(1, t.count());
        assertTrue(t.isRunning());
        assertTrue(t.elapsedMilliseconds() < 1.0);
    }

    @Test
    void doubleCloseIsIdempotent() throws Exception {
        var t = new AutoTimer();
        Thread.sleep(1);
        t.close();
        double after = t.elapsedMilliseconds();
        t.close();
        assertEquals(after, t.elapsedMilliseconds(), 0.0001);
    }
}
