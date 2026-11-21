package io.github.phiscript.input;

import io.github.phiscript.engine.BlockAreaSet;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.Viewport;
import java.util.BitSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Locks a sustained vertical shake to the physical DOWN during preparation. */
final class HoldOscillation {
    private static final int MAX_LEG_MS = 170;
    private static final int MIN_SHAKE_MS = 96;
    private HoldOscillation() {}

    static boolean stabilize(Chart chart, Viewport view, TouchEvent event, boolean flick,
            float[] x, float[] y, byte[] connected, int start, AtomicBoolean cancel, boolean rawChartAvailable) {
        if (flick || event.getNote().getType() != 3 || x.length <= MIN_SHAKE_MS) return false;
        // A shared route can extend beyond the end of its head HOLD to catch a DRAG.
        int holdSamples = (int)Math.min(x.length, Math.round(event.getNote().getEndSeconds() * 1000)
                - event.getStartMs() + 1);
        if (holdSamples <= MIN_SHAKE_MS) return false;
        BitSet locked = new BitSet(holdSamples);
        float amplitude = view.getHeight() * .12f;
        int first = 0;
        for (int i = 0; i <= holdSamples; i++) {
            if ((i & 511) == 0) check(cancel);
            if (i == holdSamples || !Float.isFinite(y[i]) || connected[i] == 0) {
                markRun(x, y, first, i, amplitude, locked, cancel, 1, 0);
                first = i < holdSamples && Float.isFinite(y[i]) ? i : i + 1;
            }
        }
        if (locked.isEmpty()) return false;
        if (rawChartAvailable) {
            // A local import can distinguish line motion from a detour around moving noise.
            // Sample the original line only for the candidate windows; never during playback.
            BitSet raw = new BitSet(holdSamples);
            final int stride = 4;
            Boolean planning = TapMod.planning.get();
            TapMod.planning.set(true);
            try {
                for (int lo = locked.nextSetBit(0); lo >= 0; ) {
                    int hi = locked.nextClearBit(lo);
                    int n = (hi-lo+stride-1)/stride;
                    float[] rawX = new float[n], rawY = new float[n];
                    int run = 0;
                    for (int i = 0; i < n; i++) {
                        if ((i & 255) == 0) check(cancel);
                        double t = Math.max(0, event.getStartMs()+lo+(long)i*stride)/1000.0;
                        Point p = chart.position(event.getNote(), t, view, false);
                        if (p == null || !Float.isFinite(p.getX()) || !Float.isFinite(p.getY())) {
                            markRun(rawX, rawY, run, i, amplitude, raw, cancel, stride, lo);
                            run = i + 1;
                        } else {
                            rawX[i] = p.getX();
                            rawY[i] = p.getY();
                        }
                    }
                    markRun(rawX, rawY, run, n, amplitude, raw, cancel, stride, lo);
                    lo = locked.nextSetBit(hi);
                }
            } finally {
                if (planning == null) TapMod.planning.remove(); else TapMod.planning.set(planning);
            }
            locked.and(raw);
            if (locked.isEmpty()) return false;
        }

        boolean noisy = TapMod.avoids(chart);
        Point down = null, previous = null;
        boolean previousChanged = false, changed = false;
        // Include the first unmodified point after the last lock to check its connection.
        int limit = Math.min(x.length, locked.length() + 1);
        for (int i = 0; i < limit; i++) {
            if ((i & 255) == 0) check(cancel);
            if (!Float.isFinite(x[i])) {
                down = previous = null;
                previousChanged = false;
                continue;
            }
            Point source = new Point(x[i], y[i]);
            if (previous == null || connected[i] == 0) down = source;
            Point target = locked.get(i) ? down : source;
            long at = (long) start + i;
            boolean guarded = noisy && BlockAreaSet.overlaps(chart,
                    at - 1 - TapMod.noiseLookBackMs(), at + TapMod.noiseLookAheadMs());
            // The original point/segment already passed the device's noise checks.
            // Release a blocked stationary anchor and re-anchor at the next safe DOWN.
            if (target != source && guarded && !TapMod.clearAt(chart, view, target, at / 1000.0)) {
                target = down = source;
                connected[i] = 0;
                changed = true;
            }
            boolean moved = target.getX() != source.getX() || target.getY() != source.getY();
            if (connected[i] != 0 && previous != null && (moved || previousChanged)) {
                // A lock boundary must not generate an instantaneous swipe to/from the anchor.
                boolean split = Math.hypot(target.getX() - previous.getX(), target.getY() - previous.getY()) > amplitude / 3;
                if (!split && guarded) {
                    try { TapMod.segment(chart, view, previous, target, at - 1, at); }
                    catch (TapMod.NoSafePoint blocked) { split = true; }
                }
                if (split) {
                    connected[i] = 0;
                    down = target;
                    changed = true;
                }
            }
            x[i] = target.getX();
            y[i] = target.getY();
            changed |= moved;
            previousChanged = moved;
            previous = target;
        }
        return changed;
    }

    private static void markRun(float[] x, float[] y, int from, int end, float amplitude,
            BitSet locked, AtomicBoolean cancel, int stride, int offset) {
        if ((end - from) * stride <= MIN_SHAKE_MS) return;
        int direction = 0, pivot = from, extreme = from, count = 0, firstLeg = from, marked = from;
        for (int i = from + 1; i < end; i++) {
            if ((i & 511) == 0) check(cancel);
            if (direction == 0) {
                float delta = y[i] - y[pivot];
                if (Math.abs(delta) >= amplitude) {
                    direction = delta > 0 ? 1 : -1;
                    extreme = i;
                }
                continue;
            }
            if (direction * (y[i] - y[extreme]) > 0) {
                extreme = i;
                // Extend the final qualified swing through its peak but never into a later pan.
                if (count >= 3 && (i-firstLeg)*stride >= MIN_SHAKE_MS && (extreme - pivot)*stride <= MAX_LEG_MS
                        && Math.abs(x[extreme]-x[pivot]) <= amplitude / 2) {
                    if (i >= marked) locked.set(offset+marked*stride, offset+(i+1)*stride);
                    marked = i + 1;
                }
            }
            if (direction * (y[extreme] - y[i]) < amplitude) continue;
            if ((extreme - pivot)*stride <= MAX_LEG_MS && (i - extreme)*stride <= MAX_LEG_MS
                    && Math.abs(x[extreme]-x[pivot]) <= amplitude / 2
                    && Math.abs(x[i]-x[extreme]) <= amplitude / 2) {
                if (count++ == 0) firstLeg = marked = pivot;
                // Three complete alternating legs and one more full-height reversal.
                if (count >= 3 && (i - firstLeg)*stride >= MIN_SHAKE_MS) {
                    if (i >= marked) locked.set(offset+marked*stride, offset+(i+1)*stride);
                    marked = i + 1;
                }
            } else count = 0;
            pivot = extreme;
            extreme = i;
            direction = -direction;
        }
    }

    private static void check(AtomicBoolean cancel) {
        if (Thread.currentThread().isInterrupted() || cancel != null && cancel.get()) throw new CancellationException();
    }
}
