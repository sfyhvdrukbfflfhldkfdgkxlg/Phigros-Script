package android.media;

/** Metadata exposed by the packet fixture in this host test. */
public class MediaFormat {
    public static final String KEY_MIME = "mime", KEY_DURATION = "durationUs",
            KEY_MAX_INPUT_SIZE = "max-input-size", KEY_FRAME_RATE = "frame-rate", KEY_ROTATION = "rotation-degrees";
    public String mime;
    public long durationUs;
    public int maxSize, fps, rotation;
    public boolean containsKey(String key) {
        return KEY_MIME.equals(key) || KEY_DURATION.equals(key) || KEY_MAX_INPUT_SIZE.equals(key)
                || KEY_FRAME_RATE.equals(key) && fps > 0 || KEY_ROTATION.equals(key) && rotation != 0;
    }
    public String getString(String key) {
        if (!KEY_MIME.equals(key)) throw new AssertionError(key);
        return mime;
    }
    public int getInteger(String key) {
        if (KEY_FRAME_RATE.equals(key) && fps > 0) return fps;
        if (KEY_MAX_INPUT_SIZE.equals(key)) return maxSize;
        if (KEY_ROTATION.equals(key)) return rotation;
        throw new AssertionError(key);
    }
    public long getLong(String key) {
        if (!KEY_DURATION.equals(key)) throw new AssertionError(key);
        return durationUs;
    }
}
