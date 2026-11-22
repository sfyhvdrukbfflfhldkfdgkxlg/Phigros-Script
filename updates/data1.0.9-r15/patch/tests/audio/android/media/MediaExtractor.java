package android.media;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaExtractor {
    public static final int SAMPLE_FLAG_SYNC = 1, SAMPLE_FLAG_ENCRYPTED = 2, SAMPLE_FLAG_PARTIAL_FRAME = 4;
    public static final int SEEK_TO_PREVIOUS_SYNC = 0;
    public static final Map<Object,Track> inputs = new HashMap<>();
    public static int live;
    public static final class Sample {
        public final long pts;
        public final int flags;
        public final byte[] data;
        public Sample(long pts, int flags, byte[] data) {
            this.pts = pts; this.flags = flags; this.data = data;
        }
    }
    public static final class Track {
        public final MediaFormat format = new MediaFormat();
        public final List<Sample> samples = new ArrayList<>();
    }
    private Track input;
    private int index;
    public MediaExtractor() { ++live; }
    public void setDataSource(FileDescriptor fd) throws IOException { open(fd); }
    public void setDataSource(String path) throws IOException { open(path); }
    private void open(Object key) throws IOException {
        input = inputs.get(key);
        if (input == null) throw new IOException("missing fixture");
    }
    public int getTrackCount() { return 1; }
    public MediaFormat getTrackFormat(int track) {
        if (track != 0) throw new AssertionError(track);
        return input.format;
    }
    public void selectTrack(int track) { getTrackFormat(track); }
    public int getSampleTrackIndex() { return index < input.samples.size() ? 0 : -1; }
    public long getSampleTime() { return getSampleTrackIndex() == -1 ? -1 : input.samples.get(index).pts; }
    public long getSampleSize() { return getSampleTrackIndex() == -1 ? -1 : input.samples.get(index).data.length; }
    public int getSampleFlags() { return input.samples.get(index).flags; }
    public int readSampleData(ByteBuffer dest, int offset) {
        if (getSampleTrackIndex() == -1) return -1;
        byte[] bytes = input.samples.get(index).data;
        dest.position(offset); dest.put(bytes); dest.limit(offset + bytes.length); dest.position(offset);
        return bytes.length;
    }
    public boolean advance() { ++index; return getSampleTrackIndex() != -1; }
    public void release() { --live; }
}
