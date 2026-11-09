package io.github.phiscript.gesturerecorder;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure Java recording model. All timestamps use the MotionEvent uptime clock. */
public final class GestureRecording {
    public static final String SCHEMA = "io.github.phiscript.gesture-recording";
    public static final int MAX_FRAMES = 100000;
    public static final long MAX_DURATION_MS = 600000;

    public static final class Pointer {
        public final int id;
        public final float x, y, pressure;
        public Pointer(int id, float x, float y, float pressure) {
            if (id < 0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(pressure))
                throw new IllegalArgumentException("Invalid pointer sample");
            this.id = id; this.x = x; this.y = y; this.pressure = pressure;
        }
    }

    public static final class Frame {
        public final long timeMs;
        public final String action;
        public final int actionPointerId;
        public final boolean historical;
        public final List<Pointer> pointers;
        Frame(long timeMs, String action, int actionPointerId, boolean historical, List<Pointer> pointers) {
            this.timeMs = timeMs; this.action = action; this.actionPointerId = actionPointerId;
            this.historical = historical;
            this.pointers = Collections.unmodifiableList(new ArrayList<>(pointers));
        }
    }

    public final int widthPx, heightPx, densityDpi, rotation;
    public final long startUptimeMs;
    private final List<Frame> frames = new ArrayList<>();
    private final List<Frame> framesView = Collections.unmodifiableList(frames);
    private final Map<Integer, Pointer> active = new LinkedHashMap<>();
    private boolean recording = true, interrupted;
    private String stopReason = "";
    private long durationMs, sampleCount;

    public GestureRecording(int widthPx, int heightPx, int densityDpi, int rotation, long startUptimeMs) {
        if (widthPx <= 0 || heightPx <= 0 || densityDpi <= 0 || startUptimeMs < 0)
            throw new IllegalArgumentException("Invalid recording canvas");
        this.widthPx = widthPx; this.heightPx = heightPx; this.densityDpi = densityDpi;
        this.rotation = rotation; this.startUptimeMs = startUptimeMs;
    }

    public boolean isRecording() { return recording; }
    public boolean wasInterrupted() { return interrupted; }
    public String getStopReason() { return stopReason; }
    public long getDurationMs() { return durationMs; }
    public long getSampleCount() { return sampleCount; }
    public List<Frame> getFrames() { return framesView; }

    /** Returns false at a resource limit; the caller should finish the recording. */
    public boolean add(long uptimeMs, String action, int actionPointerId, boolean historical, List<Pointer> pointers) {
        if (!recording) return false;
        if (uptimeMs < startUptimeMs) return true; // A batched sample predating Start is not part of this take.
        long relative = uptimeMs - startUptimeMs;
        if (relative > MAX_DURATION_MS || frames.size() >= MAX_FRAMES || sampleCount + pointers.size() > MAX_FRAMES * 10L)
            return false;
        if (!frames.isEmpty() && relative < frames.get(frames.size() - 1).timeMs) return true;
        if (!("DOWN".equals(action) || "POINTER_DOWN".equals(action) || "MOVE".equals(action)
                || "POINTER_UP".equals(action) || "UP".equals(action) || "CANCEL".equals(action)))
            throw new IllegalArgumentException("Invalid action");
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (Pointer p : pointers) if (!ids.add(p.id)) throw new IllegalArgumentException("Duplicate pointer id");
        boolean changesOne = !"MOVE".equals(action) && !"CANCEL".equals(action);
        if (changesOne && !ids.contains(actionPointerId)) throw new IllegalArgumentException("Missing action pointer");
        if (historical && !"MOVE".equals(action)) throw new IllegalArgumentException("Only MOVE can have historical samples");
        // Ignore MOVE/UP received after Start when a finger was already down.
        // A valid exported contact always begins with DOWN or POINTER_DOWN.
        if (active.isEmpty() && !"DOWN".equals(action)) return true;
        if ("DOWN".equals(action)) {
            if (!active.isEmpty() || pointers.size() != 1) throw new IllegalArgumentException("Invalid DOWN sequence");
        } else if ("POINTER_DOWN".equals(action)) {
            if (active.containsKey(actionPointerId) || pointers.size() != active.size() + 1)
                throw new IllegalArgumentException("Invalid POINTER_DOWN sequence");
            for (Integer id : active.keySet()) if (!ids.contains(id)) throw new IllegalArgumentException("Missing active pointer");
        } else {
            if (!ids.equals(active.keySet())) throw new IllegalArgumentException("Pointer state does not match DOWN");
            if ("UP".equals(action) && active.size() != 1 || "POINTER_UP".equals(action) && active.size() <= 1)
                throw new IllegalArgumentException("Invalid UP sequence");
        }
        for (Pointer p : pointers) active.put(p.id, p);
        frames.add(new Frame(relative, action, changesOne ? actionPointerId : -1, historical, pointers));
        sampleCount += pointers.size(); durationMs = relative;
        if ("POINTER_UP".equals(action) || "UP".equals(action)) active.remove(actionPointerId);
        if ("UP".equals(action) || "CANCEL".equals(action)) active.clear();
        return true;
    }

    /** Always balances active contacts, even when stopping at the frame limit. */
    public void finish(long uptimeMs, String reason, boolean interrupted) {
        if (!recording) return;
        durationMs = Math.max(durationMs, Math.min(MAX_DURATION_MS, Math.max(0, uptimeMs - startUptimeMs)));
        if (!active.isEmpty()) {
            frames.add(new Frame(durationMs, "CANCEL", -1, false, new ArrayList<>(active.values())));
            sampleCount += active.size();
            active.clear();
        }
        this.recording = false; this.interrupted = interrupted; this.stopReason = reason;
    }

    public void writeJson(Writer out, String name) throws IOException {
        if (recording) throw new IllegalStateException("Stop recording before export");
        out.write("{\n  \"schema\": "); string(out, SCHEMA);
        out.write(",\n  \"version\": 1,\n  \"name\": "); string(out, name);
        out.write(",\n  \"purpose\": \"application-gesture-editing\",\n  \"timebase\": \"android-uptime-relative-ms\",");
        out.write("\n  \"canvas\": {\"widthPx\": " + widthPx + ", \"heightPx\": " + heightPx
                + ", \"densityDpi\": " + densityDpi + ", \"rotation\": " + rotation
                + ", \"origin\": \"top-left\", \"coordinates\": \"view-local-px\", \"normalizedBy\": \"width-height\"},");
        out.write("\n  \"durationMs\": " + durationMs + ",\n  \"interrupted\": " + interrupted + ",\n  \"stopReason\": "); string(out, stopReason);
        out.write(",\n  \"frames\": [");
        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            out.write((i == 0 ? "\n" : ",\n") + "    {\"tMs\": " + f.timeMs + ", \"action\": "); string(out, f.action);
            out.write(", \"actionPointerId\": " + f.actionPointerId + ", \"historical\": " + f.historical + ", \"pointers\": [");
            for (int j = 0; j < f.pointers.size(); j++) {
                Pointer p = f.pointers.get(j);
                if (j != 0) out.write(", ");
                out.write("{\"id\": " + p.id + ", \"x\": " + p.x + ", \"y\": " + p.y
                        + ", \"nx\": " + (p.x / widthPx) + ", \"ny\": " + (p.y / heightPx) + ", \"pressure\": " + p.pressure + "}");
            }
            out.write("]}");
        }
        out.write("\n  ]\n}\n");
    }

    private static void string(Writer out, String value) throws IOException {
        out.write('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.write("\\\""); break;
                case '\\': out.write("\\\\"); break;
                case '\b': out.write("\\b"); break;
                case '\f': out.write("\\f"); break;
                case '\n': out.write("\\n"); break;
                case '\r': out.write("\\r"); break;
                case '\t': out.write("\\t"); break;
                default:
                    if (c < 32 || Character.isSurrogate(c)) out.write(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
                    else out.write(c);
            }
        }
        out.write('"');
    }
}
