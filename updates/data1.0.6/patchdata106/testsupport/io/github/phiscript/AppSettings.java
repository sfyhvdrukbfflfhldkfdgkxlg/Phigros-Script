package io.github.phiscript;
import android.content.Context;
public final class AppSettings {
 public static boolean flicks=false,combos=false,smart=false,edges=false,catchDrags=false,inward=false;
 public AppSettings(Context context){}
 public boolean flag(String key,boolean fallback){return key.equals("gesture_connect_flicks")?flicks:key.equals("gesture_combine_shots")?combos:key.equals("gesture_connect_drags")?smart:key.equals("gesture_edge_guard")?edges:key.equals("gesture_catch_drags")?catchDrags:key.equals("gesture_flick_inward")?inward:fallback;}
 public String text(String key,String fallback){return fallback;}
}
