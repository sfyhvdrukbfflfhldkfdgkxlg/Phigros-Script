package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Note;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.root.PhiraImportControl;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Timing for prepared contacts, plus visual hit positions for course-clock alignment. */
public final class PlaybackTimeline {
    private static final int MAX_NOTES = 100000; // Both native chart readers enforce this limit.

    private PlaybackTimeline() {}

    public static void write(DataOutputStream out, Chart chart, Viewport view) throws IOException {
        if (!(view.getWidth() > 0 && view.getHeight() > 0)
                || !Float.isFinite(view.getRight()) || !Float.isFinite(view.getBottom())) {
            throw new IOException(tr("预计算坐标损坏"));
        }
        List<Note> notes = chart.getNotes();
        checkHeader(chart.getOffsetSeconds(), notes.size());
        out.writeDouble(chart.getOffsetSeconds());
        out.writeInt(notes.size());
        Set<Integer> ids = new HashSet<>();
        double previous = Double.NEGATIVE_INFINITY;
        for (Note note : notes) {
            PhiraImportControl.check();
            if (!ids.add(note.getId())) throw new IOException(tr("谱面音符编号不唯一"));
            double start = note.getTimeSeconds();
            checkNote(note.getType(), start, note.getEndSeconds(), previous);
            previous = start;
            // The clock observes the visual judge position. Noise avoidance and
            // touch offsets can place the corresponding physical contact elsewhere.
            Point p = chart.position(note, start, view, true);
            float x = p == null ? Float.NaN : (p.getX() - view.getLeft()) / view.getWidth();
            float y = p == null ? Float.NaN : (p.getY() - view.getTop()) / view.getHeight();
            if (!Float.isFinite(x) || !Float.isFinite(y)) x = y = Float.NaN;
            out.writeInt(note.getId());
            out.writeByte(note.getType());
            out.writeDouble(start);
            out.writeDouble(note.getEndSeconds());
            out.writeFloat(x);
            out.writeFloat(y);
        }
    }

    public static Chart read(DataInputStream in) throws IOException {
        double offset = in.readDouble();
        int count = in.readInt();
        checkHeader(offset, count);
        if ((long)count * 29 > in.available()) throw new IOException(tr("预计算文件大小无效"));
        List<Note> notes = new ArrayList<>(count);
        Map<Integer, Point> anchors = new HashMap<>();
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            PhiraImportControl.check();
            int id = in.readInt(), type = in.readUnsignedByte();
            double start = in.readDouble(), end = in.readDouble();
            float x = in.readFloat(), y = in.readFloat();
            checkNote(type, start, end, previous);
            previous = start;
            if (anchors.containsKey(id)) throw new IOException(tr("谱面音符编号不唯一"));
            if (!(Float.isFinite(x) && Float.isFinite(y)) && !(Float.isNaN(x) && Float.isNaN(y))) {
                throw new IOException(tr("预计算坐标损坏"));
            }
            anchors.put(id, Float.isNaN(x) ? null : new Point(x, y));
            notes.add(new Note(id, type, start, end, 0, 1, 0, true, 0));
        }
        // Chart derives its first note and duration from these exact timestamps.
        // The course detector samples hit positions in a scaled camera frame.
        return Chart.Companion.fromNative$app_debug(Collections.unmodifiableList(notes), offset,
                (note, time, view) -> {
                    if (time != note.getTimeSeconds()) return null;
                    Point p = anchors.get(note.getId());
                    return p == null ? null : new Point(view.getLeft() + p.getX() * view.getWidth(),
                            view.getTop() + p.getY() * view.getHeight());
                }, (note, time, view) -> null);
    }

    private static void checkHeader(double offset, int count) throws IOException {
        if (count <= 0 || count > MAX_NOTES) throw new IOException(tr("预计算触点数量无效"));
        if (!Double.isFinite(offset)) throw new IOException(tr("谱面轨迹时间超出预计算范围"));
    }

    private static void checkNote(int type, double start, double end, double previous) throws IOException {
        if (type < 1 || type > 4 || !Double.isFinite(start) || !Double.isFinite(end)
                || start < previous || end < start) throw new IOException(tr("预计算音符已改变"));
    }
}
