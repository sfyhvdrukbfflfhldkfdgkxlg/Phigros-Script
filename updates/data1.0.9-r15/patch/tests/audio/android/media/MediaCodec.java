package android.media;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class MediaCodec {
    public static final int BUFFER_FLAG_KEY_FRAME = 1, BUFFER_FLAG_CODEC_CONFIG = 2, BUFFER_FLAG_END_OF_STREAM = 4;
    public static final int INFO_TRY_AGAIN_LATER = -1, INFO_OUTPUT_FORMAT_CHANGED = -2;
    private final ByteBuffer pcm = ByteBuffer.allocateDirect(4096);
    private final ArrayDeque<MediaExtractor.Sample> queue = new ArrayDeque<>();
    private MediaExtractor.Sample current;
    private boolean newFormat = true;
    public int dequeueInputBuffer(long timeoutUs) { return 0; }
    public ByteBuffer getInputBuffer(int index) { return pcm; }
    public void queueInputBuffer(int index, int offset, int size, long pts, int flags) {
        byte[] bytes = new byte[size];
        ByteBuffer view = pcm.duplicate(); view.position(offset); view.get(bytes);
        queue.add(new MediaExtractor.Sample(pts, flags, bytes));
    }
    public int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
        if (newFormat) { newFormat = false; return INFO_OUTPUT_FORMAT_CHANGED; }
        current = queue.poll();
        if (current == null) return INFO_TRY_AGAIN_LATER;
        info.set(0, current.data.length, current.pts, current.flags); return 0;
    }
    public MediaFormat getOutputFormat() {
        MediaFormat format = new MediaFormat(); format.mime = "audio/mp4a-latm"; return format;
    }
    public ByteBuffer getOutputBuffer(int index) { return ByteBuffer.wrap(current.data); }
    public void releaseOutputBuffer(int index, boolean render) { current = null; }
    public static final class BufferInfo {
        public int offset, size, flags;
        public long presentationTimeUs;
        public void set(int offset, int size, long ptsUs, int flags) {
            this.offset = offset;
            this.size = size;
            this.presentationTimeUs = ptsUs;
            this.flags = flags;
        }
    }
}
