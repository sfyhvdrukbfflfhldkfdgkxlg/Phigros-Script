package android.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Non-blocking direct PCM fixture, including a short read and a would-block interval. */
public class AudioRecord {
    public static final int READ_NON_BLOCKING = 1, SUCCESS = 0;
    public int channels;
    public boolean silent;
    public long firstFrameNs, frames;
    public Runnable stop;
    private int reads;
    public int read(ByteBuffer input, int limit, int mode) {
        if (!input.isDirect() || mode != READ_NON_BLOCKING) throw new AssertionError("unexpected read mode");
        int[] counts = {128, 0, 1024, 64};
        if (reads == counts.length) { stop.run(); return 0; }
        int count = counts[reads++];
        if (count * 2 * channels > limit) throw new AssertionError("bad read bound");
        input.order(ByteOrder.nativeOrder());
        for (int i = 0; i < count * channels; i++)
            input.putShort(i * 2, (short)(silent ? 0 : i % 2 == 0 ? 1000 : -1000));
        frames += count;
        return count * 2 * channels;
    }
    public int getTimestamp(AudioTimestamp stamp, int clock) {
        if (clock != AudioTimestamp.TIMEBASE_MONOTONIC) throw new AssertionError("timestamp base");
        stamp.framePosition = frames;
        stamp.nanoTime = firstFrameNs + frames * 1000000000L / 48000;
        return SUCCESS;
    }
}
