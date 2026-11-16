package io.github.phiscript.record;

/** Frame timestamps and capture geometry used by the data1.0.9 recording paths. */
public final class RecordingPolicy {
    public static final int FPS=60, BIT_RATE=16000000;
    private RecordingPolicy(){}
    public static int[] size(int width,int height){
        if(width<1||height<1)throw new IllegalArgumentException("屏幕尺寸无效");
        return width>=height?new int[]{1920,1080}:new int[]{1080,1920};
    }
    static double captureScale(int width,int height,double limit,boolean video){
        if(!video)return Math.min(1.0,limit/Math.max(width,height));
        return Math.min(1.0,Math.min(1920.0/Math.max(width,height),1080.0/Math.min(width,height)));
    }
    static long presentationNs(long frame){return frame/FPS*1000000000L+(frame%FPS)*1000000000L/FPS;}
    static long dueMs(long frame){return (presentationNs(frame)+999999L)/1000000L;}
    static long sourceSlot(long elapsedNs){return elapsedNs/1000000000L*FPS+(elapsedNs%1000000000L*FPS+500000000L)/1000000000L;}
    public static long epoch(long readyAt,int delay){
        if(delay<0||delay>15000)throw new IllegalArgumentException("手动同步延迟无效");
        return readyAt+delay;
    }
}
