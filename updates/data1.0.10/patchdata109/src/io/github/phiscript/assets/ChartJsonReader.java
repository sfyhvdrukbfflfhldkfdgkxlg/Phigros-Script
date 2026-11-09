package io.github.phiscript.assets;

import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Bounded JSON reader accepting repeated tempo rows emitted by chart editors. */
public final class ChartJsonReader {
    public static final int MAX_CHART_BYTES = 64 * 1024 * 1024;
    private ChartJsonReader() {}
    public static JSONObject read(String text) throws JSONException {
        requireSize(text);
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            if((i&4095)==0)io.github.phiscript.root.PhiraImportControl.check();
            char c = text.charAt(i);
            if (quoted) {
                if (!escaped && c == '"') quoted = false;
                if (!escaped && c == '\\') escaped = true; else escaped = false;
            } else if (c == '"') quoted = true;
            else if (c == '[' || c == '{') {
                if (++depth > 64) throw new IllegalArgumentException("Phira JSON 嵌套超过 64 层");
            } else if (c == ']' || c == '}') depth--;
        }
        JSONObject root = new JSONObject(text.startsWith("\ufeff") ? text.substring(1) : text);
        if (root.has("BPMList") && root.has("judgeLineList")) {
            JSONArray tempos = root.getJSONArray("BPMList");
            Map<Double, JSONObject> unique = new TreeMap<>();
            if (tempos.length() > 10000) throw new IllegalArgumentException("RPE BPM 数量无效");
            for (int i = 0; i < tempos.length(); i++) {
                JSONObject tempo = tempos.getJSONObject(i);
                JSONArray triple = tempo.getJSONArray("startTime");
                if (triple.length() != 3 || triple.getDouble(2) <= 0)
                    throw new IllegalArgumentException("RPE BPM 起始拍无效");
                double beat = triple.getDouble(0) + triple.getDouble(1) / triple.getDouble(2);
                double bpm = tempo.getDouble("bpm");
                if (!Double.isFinite(beat) || !Double.isFinite(bpm) || bpm <= 0 || bpm > 10000)
                    throw new IllegalArgumentException("RPE BPM 无效");
                // Stable last-row-wins for editor-exported rows at one beat.
                unique.put(beat == 0 ? 0.0 : beat, tempo);
            }
            JSONArray clean = new JSONArray();
            for (JSONObject tempo : unique.values()) clean.put(tempo);
            root.put("BPMList", clean);
            JSONArray lines = root.getJSONArray("judgeLineList");
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.getJSONObject(i);
                JSONArray layers = line.optJSONArray("eventLayers");
                if (layers != null) for (int j = 0; j < layers.length(); j++) {
                    JSONObject layer = layers.optJSONObject(j);
                    if (layer == null) continue;
                    for (String key : new String[]{"moveXEvents", "moveYEvents", "rotateEvents"}) {
                        JSONArray events = layer.optJSONArray(key);
                        if (events == null) continue;
                        Set<String> seen = new HashSet<>();
                        JSONArray distinct = new JSONArray();
                        for (int k = 0; k < events.length(); k++) {
                            JSONObject event = events.getJSONObject(k);
                            if (seen.add(event.toString())) distinct.put(event);
                        }
                        layer.put(key, distinct);
                    }
                }
                JSONArray notes = line.optJSONArray("notes");
                if (notes == null) continue;
                for (int j = 0; j < notes.length(); j++) {
                    JSONObject note = notes.getJSONObject(j);
                    // Exporters use either 0 or 2 for the lower side.
                    if (note.has("above") && note.optInt("above", 1) == 0) note.put("above", 2);
                }
            }
        }
        return root;
    }

    /** Count UTF-8 bytes without allocating a second copy of a large chart. */
    private static void requireSize(String text) {
        if (text.length() > MAX_CHART_BYTES) throw tooLarge();
        int bytes=0;
        for (int i=0;i<text.length();i++) {
            if((i&4095)==0)io.github.phiscript.root.PhiraImportControl.check();
            char c=text.charAt(i);
            if (c<=0x7f) bytes++;
            else if (c<=0x7ff) bytes+=2;
            else if (Character.isHighSurrogate(c) && i+1<text.length()
                    && Character.isLowSurrogate(text.charAt(i+1))) { bytes+=4; i++; }
            else if (Character.isSurrogate(c)) bytes++; // Java UTF-8 replacement is '?'.
            else bytes+=3;
            if (bytes>MAX_CHART_BYTES) throw tooLarge();
        }
    }

    private static IllegalArgumentException tooLarge() {
        return new IllegalArgumentException("Phira JSON 超过 64 MiB");
    }
}
