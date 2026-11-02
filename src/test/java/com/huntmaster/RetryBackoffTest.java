package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetryBackoffTest
{
    @Test public void delayDoublesUpToTheCapAndResets()
    {
        RetryBackoff backoff = new RetryBackoff(1000, 5000, () -> 1.0);
        assertEquals(1000, backoff.next());
        assertEquals(2000, backoff.next());
        assertEquals(4000, backoff.next());
        assertEquals(5000, backoff.next());
        for (int i = 0; i < 100; i++) assertEquals(5000, backoff.next());
        backoff.reset();
        assertEquals(1000, backoff.next());
    }

    @Test public void jitterOnlyShortensTheDelayByAQuarter()
    {
        assertEquals(750, new RetryBackoff(1000, 5000, () -> 0.0).next());
        RetryBackoff real = new RetryBackoff(1000, 5000);
        for (int i = 0; i < 20; i++)
        {
            long delay = real.next();
            assertTrue(delay >= 750 && delay <= 5000);
        }
    }
}
