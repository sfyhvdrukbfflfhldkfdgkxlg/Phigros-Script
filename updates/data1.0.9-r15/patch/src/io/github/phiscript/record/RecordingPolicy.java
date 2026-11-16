package io.github.phiscript.record;

/** Frame timestamps and capture geometry used by the data1.0.9 recording paths. */
public final class RecordingPolicy {
    public static final int FPS=60, BIT_RATE=16000000;
    public static final Profile DEFAULT=new Profile(1080,60);
    /** One immutable choice is shared by the codec, source sampling and output clock. */
    public static final class Profile {
        public final int shortEdge,fps,bitRate;
        private Profile(int shortEdge,int fps){
            this.shortEdge=shortEdge;this.fps=fps;
            bitRate=(shortEdge==1080?8000000:4000000)*(fps==60?2:1);
        }
        public String label(){return shortEdge+"p / "+fps+" fps";}
    }
    private RecordingPolicy(){}
    public static Profile profile(int shortEdge,int fps){
        if((shortEdge!=720&&shortEdge!=1080)||(fps!=30&&fps!=60))throw new IllegalArgumentException("不支持的录屏档位");
        return new Profile(shortEdge,fps);
    }
    public static Profile stored(String shortEdge,String fps){
        return profile("720".equals(shortEdge)?720:1080,"30".equals(fps)?30:60);
    }
    public static int[] size(int width,int height){
        return size(width,height,DEFAULT);
    }
    public static int[] size(int width,int height,Profile profile){
        if(width<1||height<1)throw new IllegalArgumentException("屏幕尺寸无效");
        int edge=profile.shortEdge,longEdge=edge==720?1280:1920;
        return width>=height?new int[]{longEdge,edge}:new int[]{edge,longEdge};
    }
    static double captureScale(int width,int height,double limit,boolean video){
        return captureScale(width,height,limit,video,DEFAULT);
    }
    static double captureScale(int width,int height,double limit,boolean video,Profile profile){
        if(width<1||height<1)throw new IllegalArgumentException("屏幕尺寸无效");
        double ocrScale=Math.min(1.0,limit/Math.max(width,height));
        if(!video)return ocrScale;
        double videoScale=Math.min(1.0,Math.min((profile.shortEdge==720?1280.0:1920.0)/Math.max(width,height),(double)profile.shortEdge/Math.min(width,height)));
        // Lower recording tiers must not reduce the pre-existing recognition resolution.
        return Math.max(ocrScale,videoScale);
    }
    static long presentationNs(long frame){return presentationNs(frame,DEFAULT);}
    static long presentationNs(long frame,Profile profile){
        if(frame<0)throw new IllegalArgumentException("帧序号无效");
        return frame/profile.fps*1000000000L+(frame%profile.fps)*1000000000L/profile.fps;
    }
    static long dueMs(long frame){return dueMs(frame,DEFAULT);}
    static long dueMs(long frame,Profile profile){return (presentationNs(frame,profile)+999999L)/1000000L;}
    static long sourceSlot(long elapsedNs){return sourceSlot(elapsedNs,DEFAULT);}
    static long sourceSlot(long elapsedNs,Profile profile){
        return elapsedNs/1000000000L*profile.fps+(elapsedNs%1000000000L*profile.fps+500000000L)/1000000000L;
    }
    public static long epoch(long readyAt,int delay){
        if(delay<0||delay>15000)throw new IllegalArgumentException("手动同步延迟无效");
        return readyAt+delay;
    }
}
