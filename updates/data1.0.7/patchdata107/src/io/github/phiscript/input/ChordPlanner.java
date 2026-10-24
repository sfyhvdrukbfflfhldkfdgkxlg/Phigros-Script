package io.github.phiscript.input;

import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.Viewport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import io.github.phiscript.engine.Note;

/** Platform-independent planning; stroke count and simultaneous fingers differ. */
public final class ChordPlanner {
    private ChordPlanner() {}
    public static final long CHUNK_MS = 32;

    public static List<TouchEvent> forChart(Chart chart) {
        return forChart(chart,true);
    }
    public static List<TouchEvent> forChart(Chart chart,boolean mergeDrags) {return forChart(chart,mergeDrags,true);}
    public static List<TouchEvent> forChart(Chart chart,boolean mergeDrags,boolean staggerFlicks) {
        List<TouchEvent> result = new ArrayList<>();
        Map<List<Object>, int[]> flicks = new HashMap<>();
        Viewport reference = new Viewport(0, 0, 1350, 900);
        Map<List<Object>, Integer> dragSlots = new HashMap<>();
        Map<List<Object>, Integer> holdSlots = new HashMap<>();
        Map<List<Object>, Integer> holdHeadCopies = new HashMap<>();
        for (TouchEvent event : TouchEvent.Companion.forChart(chart)) {
            if(event.getNote().getType()==1)event=new TouchEvent(event.getNote(),event.getStartMs(),event.getStartMs()+48);
            // Padding a hold tail by 24 ms turns a 10-to-10 chord handoff
            // into an artificial 20-finger overlap. Keep the actual tail.
            if (event.getNote().getType() == 3)
                event = new TouchEvent(event.getNote(), event.getStartMs(),
                    Math.max(event.getStartMs() + 1,
                        Math.round(event.getNote().getEndSeconds() * 1000)));
            // A flick triggers one judgement. Stacked copies need separate
            // swipes, staggered within the hit window instead of 16 fingers.
            if (staggerFlicks && event.getNote().getType() == 4) {
                io.github.phiscript.engine.Note n = event.getNote();
                // Speed and floor height affect appearance, not hit location.
                // Compare the position trajectory too, preserving native notes
                // whose animated X values diverge after a common starting point.
                List<Object> key = new ArrayList<>(Arrays.<Object>asList(n.getLineIndex(), n.getAbove(),
                    event.getStartMs(), event.getEndMs(), n.getPositionX()));
                for (int i = 0; i <= 4; i++) {
                    double seconds = (event.getStartMs()
                        + (event.getEndMs() - event.getStartMs()) * (i / 4.0)) / 1000.0;
                    Point p = chart.position(n, seconds, reference, false);
                    if (p == null) { key.add(n.getId()); break; }
                    key.add(p.getX()); key.add(p.getY());
                }
                int[] stack = flicks.get(key);
                if (stack == null) flicks.put(key, new int[]{result.size(), 1});
                else {
                    TouchEvent first = result.get(stack[0]);
                    if (stack[1] == 1) result.set(stack[0],
                        new TouchEvent(first.getNote(), first.getStartMs(), first.getStartMs() + 32));
                    long down = first.getStartMs() + stack[1]++ * 36L;
                    event = new TouchEvent(n, down, down + 32);
                }
            }
            if (event.getNote().getType() == 3) {
                Note n = event.getNote();
                List<Object> key = Arrays.<Object>asList(n.getLineIndex(), n.getAbove(), n.getPositionX());
                Integer slot = holdSlots.get(key);
                if (slot != null) {
                    TouchEvent previous = result.get(slot);
                    if (previous.getEndMs() > event.getStartMs()
                            && sameTrack(chart, previous.getNote(), n,
                                event.getStartMs(), event.getEndMs(), reference)) {
                        result.set(slot, new TouchEvent(previous.getNote(), previous.getStartMs(),
                            Math.max(previous.getEndMs(), event.getEndMs())));
                        // The head still needs a new DOWN. A short extra finger
                        // triggers it; the shared sustained contact keeps all
                        // overlapping holds on this track alive.
                        List<Object> headKey = Arrays.<Object>asList(key, event.getStartMs());
                        int copies = holdHeadCopies.containsKey(headKey) ? holdHeadCopies.get(headKey) : 0;
                        holdHeadCopies.put(headKey, copies + 1);
                        long down = event.getStartMs();
                        Note head = new Note(n.getId(), 1, n.getTimeSeconds(), n.getTimeSeconds(),
                            n.getPositionX(), n.getSpeed(), n.getFloorPosition(), n.getAbove(), n.getLineIndex());
                        result.add(new TouchEvent(head, down, down + 48));
                        continue;
                    }
                }
                holdSlots.put(key, result.size());
            }
            if (mergeDrags&&event.getNote().getType() == 2) {
                Note n = event.getNote();
                List<Object> key = Arrays.<Object>asList(n.getLineIndex(), n.getAbove(), n.getPositionX());
                Integer slot = dragSlots.get(key);
                if (slot != null) {
                    TouchEvent previous = result.get(slot);
                    if (previous.getEndMs() >= event.getStartMs()
                            && sameTrack(chart, previous.getNote(), n,
                                event.getStartMs(), event.getEndMs(), reference)) {
                        // DRAG is judged while a finger remains down. A dense
                        // run on the same track needs one continued pointer,
                        // not a new pointer for every overlapping hit window.
                        result.set(slot, new TouchEvent(previous.getNote(), previous.getStartMs(),
                            Math.max(previous.getEndMs(), event.getEndMs())));
                        continue;
                    }
                }
                dragSlots.put(key, result.size());
            }
            result.add(event);
        }
        result.sort(new Comparator<TouchEvent>() {
            @Override public int compare(TouchEvent a, TouchEvent b) {
                int order = Long.compare(a.getStartMs(), b.getStartMs());
                return order != 0 ? order : Integer.compare(a.getNote().getId(), b.getNote().getId());
            }
        });
        return result;
    }

    static boolean sameTrack(Chart chart, Note a, Note b,
            long start, long end, Viewport viewport) {
        for (int i = 0; i <= 4; i++) {
            double seconds = (start + (end - start) * (i / 4.0)) / 1000.0;
            Point pa = chart.position(a, seconds, viewport, false);
            Point pb = chart.position(b, seconds, viewport, false);
            if (pa == null || pb == null || Math.abs(pa.getX() - pb.getX()) > .01f
                    || Math.abs(pa.getY() - pb.getY()) > .01f) return false;
        }
        return true;
    }

    public static long endOfBatch(List<TouchEvent> events, int next,
            int continuingCount, long now, int strokeLimit) {
        if (continuingCount > strokeLimit)
            throw new IllegalStateException("续接触点数量超过手势容量");
        long end = now + CHUNK_MS;
        int firstExcluded = next + strokeLimit - continuingCount;
        if (firstExcluded < events.size())
            end = Math.min(end, events.get(firstExcluded).getStartMs());
        if (end <= now)
            throw new IllegalStateException("同一时刻音符超过 Android 手势容量");
        return end;
    }

    public static void checkOverlap(List<TouchEvent> all, int limit) {
        List<long[]> changes = new ArrayList<>();
        for (TouchEvent e : all) {
            if (e.getEndMs() <= e.getStartMs())
                throw new IllegalArgumentException("音符持续时间无效");
            changes.add(new long[]{e.getStartMs(), 1});
            changes.add(new long[]{e.getEndMs(), -1});
        }
        changes.sort(new Comparator<long[]>() {
            @Override public int compare(long[] a, long[] b) {
                int c = Long.compare(a[0], b[0]);
                return c != 0 ? c : Long.compare(a[1], b[1]);
            }
        });
        int count = 0;
        for (long[] c : changes) {
            count += c[1];
            if (count > limit)
                throw new IllegalStateException("此谱面需要同时按住 " + count
                    + " 个独立触点，当前触控通道上限为 " + limit);
        }
    }
}
