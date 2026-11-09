package io.github.phiscript.record;

/** Pure policy shared by both recording paths and host tests. */
public final class RecordingPolicy {
    public static final int FPS=60;
    public static final int BIT_RATE=16000000;
    private RecordingPolicy(){}
    public static int[] size(int width,int height){
        if(width<1||height<1)throw new IllegalArgumentException("屏幕尺寸无效");
        return width>=height?new int[]{1920,1080}:new int[]{1080,1920};
    }
    /** Keep the display aspect ratio before fitting it into the 16:9 video. */
    public static int[] captureSize(int width,int height){
        if(width<1||height<1)throw new IllegalArgumentException("屏幕尺寸无效");
        double scale=1080.0/Math.min(width,height);
        return new int[]{Math.max(2,((int)Math.round(width*scale)/2)*2),Math.max(2,((int)Math.round(height*scale)/2)*2)};
    }
    public static long frameTimeNs(long frame){
        if(frame<0)throw new IllegalArgumentException("帧序号无效");
        return (frame/FPS)*1000000000L+(frame%FPS)*1000000000L/FPS;
    }
    public static long frameDueMs(long frame){
        long nanos=frameTimeNs(frame);return nanos/1000000L+(nanos%1000000L==0?0:1);
    }
    public static long epoch(long readyAt,int delay){
        if(delay<0||delay>15000)throw new IllegalArgumentException("手动同步延迟无效");
        return readyAt+delay;
    }
}
