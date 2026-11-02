package com.huntmaster;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Doubling delay with jitter, so a whole clan does not retry a recovering bot in step. */
final class RetryBackoff
{
    private final long baseMs, capMs;
    private final DoubleSupplier random;
    private int failures;

    RetryBackoff(long baseMs, long capMs) { this(baseMs, capMs, () -> ThreadLocalRandom.current().nextDouble()); }
    RetryBackoff(long baseMs, long capMs, DoubleSupplier random) { this.baseMs = baseMs; this.capMs = capMs; this.random = random; }

    long next()
    {
        long delay = Math.min(capMs, baseMs << Math.min(failures, 20));
        failures++;
        return (long) (delay * (0.75 + 0.25 * random.getAsDouble()));
    }
    void reset() { failures = 0; }
}
