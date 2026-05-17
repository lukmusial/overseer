package com.hft.core.util;

/**
 * Cache-line-isolated {@code volatile long} holder, using the sandwich
 * inheritance pattern from LMAX Disruptor's own {@code Sequence} class.
 *
 * <p>Purpose: when two threads repeatedly write/read different long fields
 * that happen to sit in the same 64-byte cache line, the CPU's cache-coherence
 * protocol bounces the line back and forth between cores — "false sharing" —
 * producing stalls out of proportion to the work. This class ensures the
 * {@code value} field sits alone in its cache line regardless of what else
 * the owning object declares near it.
 *
 * <p>Layout (56+8+56 = 120 bytes of instance data, plus 16-byte object
 * header ≈ 136 bytes per instance). The volatile field is sandwiched between
 * 7 long padding fields on each side, guaranteeing no other field on the same
 * object ends up on the same 64-byte line.
 *
 * <p>Inheritance — rather than declaring the padding fields in a single class —
 * prevents the JVM from reordering unused fields away: the Java Memory Model
 * permits the JIT to elide "dead" padding fields declared in the same class,
 * but NOT to reorder fields declared in a superclass relative to fields in a
 * subclass.
 *
 * <p>When {@code PaddedVolatileLong} is cheaper than {@code volatile long}:
 * <ul>
 *   <li>Fields read by one thread and written by another, i.e. anything that
 *       flows from the Disruptor consumer to UI/snapshot threads.</li>
 *   <li>Fields updated at very high frequency (per-tick) where the line
 *       ping-pong shows up in profiles.</li>
 * </ul>
 *
 * <p>When to stick with a bare {@code volatile long}:
 * <ul>
 *   <li>Single-threaded state (no other thread reads it).</li>
 *   <li>Very rare writes where the 136-byte cost is unjustified.</li>
 * </ul>
 */
public final class PaddedVolatileLong extends PaddedVolatileLongRhs {

    public PaddedVolatileLong() {
        this(0L);
    }

    public PaddedVolatileLong(long initial) {
        this.value = initial;
    }

    public long get() {
        return value;
    }

    public void set(long newValue) {
        this.value = newValue;
    }

    /**
     * Not atomic — a plain read-add-write under the hood. Safe only when a
     * single writer is guaranteed by the caller's invariants (e.g. all writes
     * happen on the Disruptor consumer thread). This matches the current
     * {@code PositionManager} usage, which runs its aggregate-update arithmetic
     * under the single-writer-principle of the Disruptor consumer.
     */
    public long addAndGet(long delta) {
        long next = value + delta;
        value = next;
        return next;
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}

// --- Sandwich layout scaffolding ---
// Field layout by superclass so the JVM cannot reorder the padding away.

@SuppressWarnings("unused")
class PaddedVolatileLongLhs {
    // 7 longs of left-hand padding — ensures no field of an earlier sibling
    // object ends up in the same cache line as `value` below.
    private long p01, p02, p03, p04, p05, p06, p07;
}

class PaddedVolatileLongValue extends PaddedVolatileLongLhs {
    protected volatile long value;
}

@SuppressWarnings("unused")
class PaddedVolatileLongRhs extends PaddedVolatileLongValue {
    // 7 longs of right-hand padding — ensures no subsequently-declared field
    // (or the start of the next allocated object) lands on `value`'s line.
    private long p11, p12, p13, p14, p15, p16, p17;
}
