package io.github.phiscript.record;

/** Pure policy shared by both recording paths and host tests. */
public final class RecordingPolicy {
    private RecordingPolicy(){}
    public static int[] size(int width,int height){
        if(width<1||height<1)throw new IllegalArgumentException("屏幕尺寸无效");
        double scale=Math.min(1.0,1280.0/Math.max(width,height));
        return new int[]{Math.max(16,((int)(width*scale)/16)*16),Math.max(16,((int)(height*scale)/16)*16)};
    }
    public static long epoch(long readyAt,int delay){
        if(delay<0||delay>15000)throw new IllegalArgumentException("手动同步延迟无效");
        return readyAt+delay;
    }
}
