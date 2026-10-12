package com.neueda.leap.time;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;

class SystemClockTest {
    @Test void exposesSingletonAndReturnsCurrentTime() {
        Clock clock = SystemClock.getInstance();
        assertSame(clock, SystemClock.getInstance());
        Instant before = Instant.now();
        Instant actual = clock.now();
        Instant after = Instant.now();
        assertFalse(actual.isBefore(before));
        assertFalse(actual.isAfter(after));
    }
}
