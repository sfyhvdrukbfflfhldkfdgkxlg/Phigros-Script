package io.github.phiscript.gesturerecorder;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** One private, atomic recovery draft; no storage permission or cloud service. */
final class DraftStore {
    static final class Draft {
        final String name; final GestureRecording recording;
        Draft(String name, GestureRecording recording) { this.name = name; this.recording = recording; }
    }
    static void save(File directory, GestureRecording recording, String name) {
        AtomicFile draft = new AtomicFile(new File(directory, "last-recording.json"));
        FileOutputStream bytes = null;
        try {
            bytes = draft.startWrite();
            OutputStreamWriter writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
            recording.writeJson(writer, name); writer.flush(); draft.finishWrite(bytes);
        } catch (Exception failed) { if (bytes != null) draft.failWrite(bytes); }
    }
    static void clear(File directory) { new AtomicFile(new File(directory, "last-recording.json")).delete(); }
    static Draft load(File directory) {
        try {
            AtomicFile draft = new AtomicFile(new File(directory, "last-recording.json"));
            if (!draft.getBaseFile().isFile() || draft.getBaseFile().length() > 150000000) return null;
            JSONObject root = new JSONObject(new String(draft.readFully(), StandardCharsets.UTF_8));
            if (!GestureRecording.SCHEMA.equals(root.getString("schema")) || root.getInt("version") != 1) return null;
            JSONObject canvas = root.getJSONObject("canvas");
            GestureRecording recording = new GestureRecording(canvas.getInt("widthPx"), canvas.getInt("heightPx"),
                    canvas.getInt("densityDpi"), canvas.getInt("rotation"), 0);
            JSONArray frames = root.getJSONArray("frames");
            // The final synthesized CANCEL may exceed the regular frame cap by one.
            for (int i = 0; i < frames.length(); i++) {
                JSONObject frame = frames.getJSONObject(i); JSONArray points = frame.getJSONArray("pointers");
                List<GestureRecording.Pointer> pointers = new ArrayList<>();
                for (int p = 0; p < points.length(); p++) {
                    JSONObject point = points.getJSONObject(p);
                    pointers.add(new GestureRecording.Pointer(point.getInt("id"), (float)point.getDouble("x"),
                            (float)point.getDouble("y"), (float)point.getDouble("pressure")));
                }
                if (!recording.add(frame.getLong("tMs"), frame.getString("action"), frame.getInt("actionPointerId"),
                        frame.getBoolean("historical"), pointers)) break;
            }
            recording.finish(root.getLong("durationMs"), root.getString("stopReason"), root.getBoolean("interrupted"));
            return new Draft(root.optString("name", "新手势"), recording);
        } catch (Exception unavailable) { return null; }
    }
}
