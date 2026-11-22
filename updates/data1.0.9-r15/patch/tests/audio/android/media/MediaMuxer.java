package android.media;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaMuxer {
    public static class OutputFormat { public static final int MUXER_OUTPUT_MPEG_4 = 0; }
    public static final Map<FileDescriptor,List<MediaExtractor.Track>> output = new HashMap<>();
    public static int live;
    public static boolean injectFailure;
    public static int orientation;
    private final List<MediaExtractor.Track> tracks = new ArrayList<>();
    private boolean started;
    public MediaMuxer(FileDescriptor fd, int format) {
        if (format != OutputFormat.MUXER_OUTPUT_MPEG_4) throw new AssertionError(format);
        output.put(fd, tracks); ++live;
    }
    public int addTrack(MediaFormat format) {
        if (started) throw new AssertionError("late track");
        MediaExtractor.Track t = new MediaExtractor.Track();
        t.format.mime = format.mime;
        tracks.add(t);
        return tracks.size() - 1;
    }
    public void setOrientationHint(int degrees) { orientation = degrees; }
    public void start() { if (started) throw new AssertionError(); started = true; }
    public void writeSampleData(int track, ByteBuffer data, MediaCodec.BufferInfo info) {
        if (!started) throw new AssertionError("muxer not started");
        if (injectFailure) throw new IllegalStateException("injected mux failure");
        byte[] bytes = new byte[info.size];
        ByteBuffer view = data.duplicate();
        view.position(info.offset); view.get(bytes);
        tracks.get(track).samples.add(new MediaExtractor.Sample(info.presentationTimeUs, info.flags, bytes));
    }
    public void stop() { if (!started) throw new AssertionError(); started = false; }
    public void release() { --live; }
}
