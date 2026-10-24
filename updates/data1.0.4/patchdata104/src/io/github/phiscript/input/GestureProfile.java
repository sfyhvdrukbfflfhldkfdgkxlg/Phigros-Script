package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;

/** A snapshot: settings never change an already running gesture. */
public final class GestureProfile {
    public final boolean connectDrags;
    public final boolean catchDrags;
    public final int gapMs;
    public final float distance;
    public GestureProfile(boolean enabled,int gapMs,float distance){
        this(enabled,gapMs,distance,false);
    }
    public GestureProfile(boolean enabled,int gapMs,float distance,boolean catchDrags){
        if(gapMs<20||gapMs>300||!Float.isFinite(distance)||distance<.01f||distance>.5f)throw new IllegalArgumentException("连键手势参数超出范围");
        this.connectDrags=enabled;this.gapMs=gapMs;this.distance=distance;
        this.catchDrags=catchDrags;
    }
    public static GestureProfile read(Context context){
        if(context==null)return new GestureProfile(true,70,.12f,true);
        AppSettings prefs=new AppSettings(context);
        return new GestureProfile(prefs.flag("gesture_connect_drags",true),Integer.parseInt(prefs.text("gesture_link_gap_ms","70")),Float.parseFloat(prefs.text("gesture_link_distance","0.12")),prefs.flag("gesture_catch_drags",true));
    }
    @Override public boolean equals(Object other){if(!(other instanceof GestureProfile))return false;GestureProfile p=(GestureProfile)other;return connectDrags==p.connectDrags&&catchDrags==p.catchDrags&&gapMs==p.gapMs&&Float.compare(distance,p.distance)==0;}
    @Override public int hashCode(){return gapMs*31+Float.floatToIntBits(distance)+(connectDrags?1:0)+(catchDrags?2:0);}
}
