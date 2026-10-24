package io.github.phiscript.capture;

/** Distinct fresh capture frames only; repeated snapshots cannot authorize an action. */
public final class AutoEvidence {
    private String key;
    private long first=-1,last=-1;
    private int count;
    public void reset(){key=null;first=last=-1;count=0;}
    public boolean observe(String value,long frame,long now){
        if(frame<0||frame>now||now-frame>900){reset();return false;}
        if(frame<=last)return false;
        if(value==null||!value.equals(key)||last<0||frame-last>2000){key=value;first=frame;count=0;}
        last=frame;if(value==null)return false;count++;
        return count>=2&&frame-first>=150;
    }
}
