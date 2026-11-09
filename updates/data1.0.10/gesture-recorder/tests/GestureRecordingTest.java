package io.github.phiscript.gesturerecorder;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

public final class GestureRecordingTest {
    private static GestureRecording.Pointer p(int id, float x, float y) {
        return new GestureRecording.Pointer(id, x, y, .75f);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        GestureRecording g = new GestureRecording(100, 200, 320, 1, 1000);
        check(g.add(1005, "MOVE", -1, true, Arrays.asList(p(7, 10, 20))), "Ignore touch begun outside take");
        check(g.getFrames().isEmpty(), "Do not export unbalanced initial MOVE");
        g.add(1010, "DOWN", 7, false, Arrays.asList(p(7, 10, 20)));
        g.add(1015, "POINTER_DOWN", 2, false, Arrays.asList(p(7, 11, 22), p(2, 30, 40)));
        g.add(1016, "MOVE", -1, true, Arrays.asList(p(7, 12, 24), p(2, 32, 42)));
        g.add(1020, "MOVE", -1, false, Arrays.asList(p(7, 15, 30), p(2, 35, 45)));
        g.add(1020, "POINTER_UP", 7, false, Arrays.asList(p(7, 15, 30), p(2, 35, 45)));
        g.add(1030, "UP", 2, false, Arrays.asList(p(2, 36, 46)));
        // Android may reuse a pointer ID in a later stroke.
        g.add(1040, "DOWN", 7, false, Arrays.asList(p(7, -10, 220)));
        g.finish(1050, "activity-paused", true);
        check(g.getFrames().size() == 8, "Historical + current + CANCEL frames preserved");
        check(g.getFrames().get(2).historical, "Batched samples marked");
        check(g.getFrames().get(4).timeMs == 20, "Same-millisecond frame order retained");
        check(g.getFrames().get(7).action.equals("CANCEL"), "Finish balances contact");
        check(g.getDurationMs() == 50 && g.wasInterrupted(), "Stop metadata");
        check(!g.add(1051, "MOVE", -1, false, Collections.emptyList()), "Frozen after Stop");
        StringWriter json = new StringWriter(); g.writeJson(json, "拖动\"\\\n\t\u0001😀");
        String result = json.toString();
        check(result.contains("\"nx\": -0.1") && result.contains("\"ny\": 1.1"), "Preserve out-of-canvas coordinates");
        check(result.contains("\\\"\\\\\\n\\t\\u0001\\ud83d\\ude00"), "JSON name escaped");
        System.out.print(result);
        GestureRecording invalid = new GestureRecording(10, 10, 160, 0, 0);
        invalid.add(0, "DOWN", 0, false, Arrays.asList(p(0, 1, 1)));
        boolean failed = false;
        try { invalid.add(1, "MOVE", -1, false, Arrays.asList(p(1, 1, 1))); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Reject pointer without DOWN");
        failed = false;
        try { new GestureRecording.Pointer(0, Float.NaN, 0, 1); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Reject nonfinite coordinates");
        invalid.finish(1, "user-stopped", false);
        invalid.finish(100, "again", true);
        check(invalid.getDurationMs() == 1, "Finish idempotent");
        GestureRecording completed = new GestureRecording(10, 10, 160, 0, 2000);
        completed.add(2005, "DOWN", 0, false, Arrays.asList(p(0, 1, 1)));
        completed.add(2004, "MOVE", -1, true, Arrays.asList(p(0, 3, 3)));
        check(completed.getFrames().size() == 1, "Discard delayed stale history instead of reversing time");
        completed.add(2010, "UP", 0, false, Arrays.asList(p(0, 1, 1)));
        completed.finish(2020, "user-stopped", false);
        check(completed.getFrames().size() == 2 && !completed.wasInterrupted(), "Completed touch has no extra CANCEL");
        boolean immutable = false;
        try { completed.getFrames().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Export snapshot cannot mutate frames");
        GestureRecording duration = new GestureRecording(10, 10, 160, 0, 1000);
        duration.add(1001, "DOWN", 0, false, Arrays.asList(p(0, 1, 1)));
        check(!duration.add(1001 + GestureRecording.MAX_DURATION_MS, "MOVE", -1, false, Arrays.asList(p(0, 2, 2))), "Duration cap");
        duration.finish(1001 + GestureRecording.MAX_DURATION_MS, "duration-limit", true);
        check(duration.getDurationMs() == GestureRecording.MAX_DURATION_MS, "Duration bounded on finish");
        check(duration.getFrames().get(1).action.equals("CANCEL"), "Duration limit balances pointers");
        GestureRecording limit = new GestureRecording(10, 10, 160, 0, 0);
        limit.add(0, "DOWN", 0, false, Arrays.asList(p(0, 1, 1)));
        for (int i = 1; i < GestureRecording.MAX_FRAMES; i++) check(limit.add(i, "MOVE", -1, i % 2 == 0, Arrays.asList(p(0, 1, 1))), "Accept cap");
        check(!limit.add(GestureRecording.MAX_FRAMES, "MOVE", -1, false, Arrays.asList(p(0, 1, 1))), "Bound frame memory");
        limit.finish(GestureRecording.MAX_FRAMES, "sample-limit", true);
        check(limit.getFrames().size() == GestureRecording.MAX_FRAMES + 1, "Resource limit still closes pointers");
        check(limit.getFrames().get(GestureRecording.MAX_FRAMES).action.equals("CANCEL"), "Limit CANCEL");
        System.err.println("PASS: multi-touch, history, uptime, interruption, JSON escaping, pointer IDs, memory bounds");
    }
}
