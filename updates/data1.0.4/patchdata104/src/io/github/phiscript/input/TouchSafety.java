package io.github.phiscript.input;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowManager;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.Viewport;

/** Avoid system-owned edge gestures without changing the game's chart projection. */
public final class TouchSafety {
    public final boolean enabled;
    public final boolean inwardFlick;
    public final TouchBounds bounds;
    private TouchSafety(boolean enabled,boolean inward,TouchBounds bounds){this.enabled=enabled;this.inwardFlick=inward;this.bounds=bounds;}
    public static TouchSafety read(Context context,int width,int height,Viewport viewport){
        AppSettings prefs=new AppSettings(context);boolean enabled=prefs.flag("gesture_edge_guard",true);
        float left=0,top=0,right=0,bottom=0;
        if(enabled){
            float density=context.getResources().getDisplayMetrics().density;
            if(!Float.isFinite(density)||density<=0)density=1;
            left=right=margin(prefs,"gesture_edge_side_dp",20)*density;
            top=margin(prefs,"gesture_edge_top_dp",24)*density;bottom=margin(prefs,"gesture_edge_bottom_dp",32)*density;
            if(Build.VERSION.SDK_INT>=30){
                try{
                    WindowInsets insets=((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getMaximumWindowMetrics().getWindowInsets();
                    Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                    Insets gestures=insets.getInsets(WindowInsets.Type.systemGestures()|WindowInsets.Type.mandatorySystemGestures());
                    left=Math.max(left,Math.max(bars.left,gestures.left)+2);top=Math.max(top,Math.max(bars.top,gestures.top)+2);
                    right=Math.max(right,Math.max(bars.right,gestures.right)+2);bottom=Math.max(bottom,Math.max(bars.bottom,gestures.bottom)+2);
                }catch(RuntimeException ignored){/* Older OEM window implementations: retain explicit margins. */}
            }
        }
        TouchBounds area=new TouchBounds(left,top,width-1-right,height-1-bottom);
        if(viewport!=null)area=area.intersect(Math.max(0,viewport.getLeft()),Math.max(0,viewport.getTop()),
            Math.min(width-1,Math.nextDown(viewport.getRight())),Math.min(height-1,Math.nextDown(viewport.getBottom())));
        return new TouchSafety(enabled,prefs.flag("gesture_flick_inward",true),area);
    }
    private static float margin(AppSettings prefs,String key,int fallback){
        try{float value=Float.parseFloat(prefs.text(key,Integer.toString(fallback)));if(Float.isFinite(value)&&value>=0&&value<=96)return value;}catch(NumberFormatException ignored){}
        return fallback;
    }
    public Point point(GesturePlan plan,Chart chart,TouchEvent event,long time,Viewport viewport,int[] screen,boolean[] bounded){
        if(!enabled&&!inwardFlick)return plan.point(chart,event,time,viewport,screen,bounded);
        Point p;
        if(event.getNote().getType()==4&&inwardFlick){
            double seconds=Math.max(0,Math.max(event.getStartMs(),Math.min(event.getEndMs(),time)))/1000.0;
            p=chart.position(event.getNote(),seconds,viewport,false);if(p==null)throw new IllegalArgumentException("无法计算划键位置");
            Point normal=chart.judgeNormal(event.getNote(),seconds,viewport);
            float x=bounds.x(p.getX()),y=bounds.y(p.getY());float[] target=bounds.flick(x,y,normal.getX()*viewport.getHeight()*.1f,normal.getY()*viewport.getHeight()*.1f);
            float progress=Math.max(0,Math.min(1,(time-event.getStartMs())/60f));
            if(x!=p.getX()||y!=p.getY())bounded[0]=true;
            p=new Point(x+(target[0]-x)*progress,y+(target[1]-y)*progress);
        }else p=plan.point(chart,event,time,viewport,screen,bounded);
        float x=bounds.x(p.getX()),y=bounds.y(p.getY());if(x!=p.getX()||y!=p.getY())bounded[0]=true;
        return new Point(x,y);
    }
}
