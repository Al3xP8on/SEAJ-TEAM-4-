package com.neueda.leap.time;

import java.time.Instant;

/**
 * A source of the current time. Exists purely so anything that needs "now"
 * - {@code Account.touch()}, an order's {@code createdOn} - depends on this
 * small interface instead of calling {@link Instant#now()} directly.
 *
 * <p><b>Contract</b>
 * <ul>
 *   <li><b>Preconditions:</b> none.</li>
 *   <li><b>Postconditions:</b> returns a non-null instant; successive calls
 *       on the same {@code Clock} never go backwards.</li>
 *   <li><b>Invariants:</b> a given {@code Clock} instance's notion of "now"
 *       is entirely up to its implementation - {@link SystemClock} reads the
 *       real system clock, while a test double can fix it to any instant and
 *       hold it still, which is the whole reason this interface exists: it
 *       turns "assert the timestamp is roughly now" into "assert the
 *       timestamp equals exactly this instant."</li>
 * </ul>
 */
public interface Clock {

    Instant now();
}
