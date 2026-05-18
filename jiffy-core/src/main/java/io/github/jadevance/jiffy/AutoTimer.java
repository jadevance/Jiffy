package io.github.jadevance.jiffy;

public final class AutoTimer implements AutoCloseable {

    private long startNanos;
    private long accumulatedNanos;
    private int count = 1;
    private boolean running = true;

    public AutoTimer() {
        this.startNanos = System.nanoTime();
    }

    public double elapsedMilliseconds() {
        long total = accumulatedNanos + (running ? System.nanoTime() - startNanos : 0L);
        return total / 1_000_000.0;
    }

    public int count() {
        return count;
    }

    public boolean isRunning() {
        return running;
    }

    public AutoTimer resume() {
        if (!running) {
            startNanos = System.nanoTime();
            running = true;
            count++;
        }
        return this;
    }

    public AutoTimer startOver() {
        accumulatedNanos = 0L;
        startNanos = System.nanoTime();
        count = 1;
        running = true;
        return this;
    }

    @Override
    public void close() {
        if (running) {
            accumulatedNanos += System.nanoTime() - startNanos;
            running = false;
        }
    }
}
