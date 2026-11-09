package io.github.phiscript.capture;

/** Clock is anchored to post-retry screen evidence, never to su or a retry-click timer. */
public final class PhigrosStartGate {
    private long command=-1,first=-1,last=-1;
    private int count;
    private boolean locked,failed;
    public void command(long at){if(at<0)throw new IllegalArgumentException("重试时刻无效");command=at;first=last=-1;count=0;locked=failed=false;}
    public long observe(long frame,long now,boolean hud,boolean menu,boolean restartEvidence){
        if(locked||failed)return -1;
        if(command<0||frame<=command||frame>now||now-frame>250||frame<=last)return -1;
        if(count>=2&&(!hud||menu||now-first>900)){failed=true;return -1;}
        // Two consecutive HUD frames may be followed by bounded restart verification.
        if(!hud||menu||last>=0&&frame-last>(count>=2?900:180)){first=-1;count=0;}
        last=frame;
        if(!hud||menu)return -1;
        if(first<0)first=frame;
        count++;
        if(count<2||frame-first<30||!restartEvidence||now-first>900)return -1;
        locked=true;return first;
    }
    public static long epoch(long visibleAt,int captureLag,int offset,int touchOffset){
        if(visibleAt<0||captureLag< -500||captureLag>500||offset< -3000||offset>10000||touchOffset< -500||touchOffset>500)
            throw new IllegalArgumentException("Phigros 自动同步参数无效");
        return visibleAt-captureLag+offset+touchOffset;
    }
}
