package com.neueda.leap.time;

import java.time.Instant;

/**
 * The real {@link Clock}: delegates straight to {@link Instant#now()}.
 * A stateless singleton - implemented as the traditional Java singleton
 * (private constructor, a private static field holding the one instance,
 * and a public static {@link #getInstance()} accessor) rather than the
 * single-element-enum idiom.
 */
public final class SystemClock implements Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    public static SystemClock getInstance() {
        return INSTANCE;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }
}
