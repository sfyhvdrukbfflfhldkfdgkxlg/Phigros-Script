package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.content.Context;
import android.content.SharedPreferences;
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
    static TouchSafety forPreparation(TouchBounds bounds){return new TouchSafety(TapMod.preparationEdgeGuard(),TapMod.preparationInwardFlick(),bounds);}
    public static TouchSafety read(Context context,int width,int height,Viewport viewport){
        return read(context,width,height,viewport,false);
    }
    static TouchSafety read(Context context,int width,int height,Viewport viewport,boolean importingFromPortrait){
        AppSettings prefs=new AppSettings(context);boolean enabled=prefs.flag("gesture_edge_guard",true);
        float left=0,top=0,right=0,bottom=0;
        if(enabled){
            float density=context.getResources().getDisplayMetrics().density;
            if(!Float.isFinite(density)||density<=0)density=1;
            left=right=margin(prefs,"gesture_edge_side_dp",20)*density;
            top=margin(prefs,"gesture_edge_top_dp",24)*density;bottom=margin(prefs,"gesture_edge_bottom_dp",32)*density;
            int[] edges = systemEdges(context, width, height);
            if (width > height && (importingFromPortrait || context instanceof TouchService)) {
                reuseLandscapeEdges(context, width, height, density, importingFromPortrait, edges);
            }
            // Allow for integer rounding at the system gesture boundary.
            for (int i = 0; i < edges.length; i++) {
                if (edges[i] > 0 || Build.VERSION.SDK_INT >= 30) edges[i] += 2;
            }
            left = Math.max(left, edges[0]);
            top = Math.max(top, edges[1]);
            right = Math.max(right, edges[2]);
            bottom = Math.max(bottom, edges[3]);
        }
        TouchBounds area=new TouchBounds(left,top,width-1-right,height-1-bottom);
        if(viewport!=null)area=area.intersect(Math.max(0,viewport.getLeft()),Math.max(0,viewport.getTop()),
            Math.min(width-1,Math.nextDown(viewport.getRight())),Math.min(height-1,Math.nextDown(viewport.getBottom())));
        return new TouchSafety(enabled,prefs.flag("gesture_flick_inward",true),area);
    }
    private static int[] systemEdges(Context context, int width, int height) {
        int[] edges = new int[4];
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return edges;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                WindowInsets insets = manager.getMaximumWindowMetrics().getWindowInsets();
                Insets bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets gestures = insets.getInsets(WindowInsets.Type.systemGestures() | WindowInsets.Type.mandatorySystemGestures());
                return new int[] {Math.max(bars.left, gestures.left), Math.max(bars.top, gestures.top),
                        Math.max(bars.right, gestures.right), Math.max(bars.bottom, gestures.bottom)};
            } catch (RuntimeException unavailable) {
                // Some service contexts do not expose WindowMetrics. Try the display below.
            }
        }
        try {
            android.view.Display display = manager.getDefaultDisplay();
            android.util.DisplayMetrics full = new android.util.DisplayMetrics();
            android.util.DisplayMetrics usable = new android.util.DisplayMetrics();
            display.getRealMetrics(full);
            display.getMetrics(usable);
            if (full.widthPixels != width || full.heightPixels != height) return edges;
            // Legacy DisplayMetrics excludes permanent decor from the application size.
            // Ignore window-sized differences (for example letterboxing or split screen).
            int side = width - usable.widthPixels;
            int foot = height - usable.heightPixels;
            int limit = Math.min(width, height) / 4;
            if (side > 0 && side <= limit) {
                int rotation = display.getRotation();
                if (rotation != android.view.Surface.ROTATION_90) edges[0] = side;
                if (rotation != android.view.Surface.ROTATION_270) edges[2] = side;
            }
            if (foot > 0 && foot <= limit) edges[3] = foot;
        } catch (RuntimeException unavailable) {
            // The configured edge margins still apply.
        }
        return edges;
    }

    private static void reuseLandscapeEdges(Context context, int width, int height, float density,
            boolean portrait, int[] edges) {
        SharedPreferences observed = context.getSharedPreferences("game_window_insets", Context.MODE_PRIVATE);
        boolean matches = observed.getInt("width", -1) == width && observed.getInt("height", -1) == height
                && observed.getFloat("density", 0) == density;
        String[] keys = {"left", "top", "right", "bottom"};
        if (portrait) {
            if (matches) {
                for (int i = 0; i < edges.length; i++) edges[i] = observed.getInt(keys[i], edges[i]);
            }
            return;
        }
        boolean changed = !matches;
        for (int i = 0; i < edges.length; i++) changed |= observed.getInt(keys[i], -1) != edges[i];
        if (changed) {
            SharedPreferences.Editor next = observed.edit().putInt("width", width).putInt("height", height)
                    .putFloat("density", density);
            for (int i = 0; i < edges.length; i++) next.putInt(keys[i], edges[i]);
            next.apply();
        }
    }
    private static float margin(AppSettings prefs,String key,int fallback){
        try{float value=Float.parseFloat(prefs.text(key,Integer.toString(fallback)));if(Float.isFinite(value)&&value>=0&&value<=96)return value;}catch(NumberFormatException ignored){}
        return fallback;
    }
    public Point point(GesturePlan plan,Chart chart,TouchEvent event,long time,Viewport viewport,int[] screen,boolean[] bounded){
        return point(plan,chart,event,time,viewport,screen,bounded,null,time);
    }
    public Point point(GesturePlan plan,Chart chart,TouchEvent event,long time,Viewport viewport,int[] screen,boolean[] bounded,Point previous,long previousTime){
        if(plan.hasPreparedTrajectory())return plan.preparedPoint(event,time,previous,previousTime);
        return calculate(plan,chart,event,time,viewport,screen,bounded,previous,previousTime,false);
    }
    /** A successful prior-point result certifies the exact previous/result
     * connection for these chart milliseconds, including both timing guards.
     * PreparedTrajectory relies on this instead of checking it a second time. */
    Point preparePoint(GesturePlan plan,Chart chart,TouchEvent event,long time,Viewport viewport,int[] screen,boolean[] bounded,Point previous,long previousTime){return calculate(plan,chart,event,time,viewport,screen,bounded,previous,previousTime,true);}
    private Point escape(Chart chart,io.github.phiscript.engine.Note note,double time,Viewport viewport,Point p,Point previous,long previousTime,boolean offline,boolean pureHold,boolean preferInterior){
        if(offline){
            long now=Math.round(time*1000),from=previous==null?now:Math.min(previousTime,now),to=previous==null?now:Math.max(previousTime,now);
            // With no active geometry anywhere over the entire connection and
            // its timing guard, withinBounds and segment both return this exact
            // clamped point. Check the whole span, not only the sample instant:
            // a short region can activate between two otherwise clear points.
            if(!TapMod.avoids(chart)||!io.github.phiscript.engine.BlockAreaSet.overlaps(chart,from-TapMod.noiseLookBackMs(),to+TapMod.noiseLookAheadMs()))return new Point(bounds.x(p.getX()),bounds.y(p.getY()));
        }
        return offline?TapMod.withinBounds(chart,note,time,viewport,p,bounds,previous,previousTime,pureHold,preferInterior):TapMod.runtimeWithinBounds(chart,note,time,viewport,p,bounds,previous,previousTime,pureHold,preferInterior);
    }
    private Point calculate(GesturePlan plan,Chart chart,TouchEvent event,long time,Viewport viewport,int[] screen,boolean[] bounded,Point previous,long previousTime,boolean offline){
        ContactPlanner.Route route=plan.routes.get(event);
        boolean pureHold=event.getNote().getType()==3 && !plan.hasFlickPath(event) && (route==null||route.uses==1);
        if(route!=null){
            Boolean priorPlanning=TapMod.planning.get();TapMod.planning.set(true);Point p;
            try{p=route.at(chart,time,viewport,bounds);}
            finally{if(priorPlanning==null)TapMod.planning.remove();else TapMod.planning.set(priorPlanning);}
            ContactPlanner.Anchor anchor=route.anchor(time);
            return escape(chart,anchor.note,time/1000.0,viewport,p,previous,previousTime,offline,pureHold,!anchor.precise&&!plan.hasFlickPath(event));
        }
        if(!enabled&&!inwardFlick)return escape(chart,event.getNote(),time/1000.0,viewport,plan.point(chart,event,time,viewport,screen,bounded),previous,previousTime,offline,pureHold,!plan.hasFlickPath(event));
        Point p;
        if(plan.hasFlickPath(event))p=plan.point(chart,event,time,viewport,screen,bounded,bounds);
        else if(event.getNote().getType()==4&&inwardFlick){
            double seconds=Math.max(0,Math.max(event.getStartMs(),Math.min(event.getEndMs(),time)))/1000.0;
            p=chart.position(event.getNote(),seconds,viewport,false);if(p==null)throw new IllegalArgumentException(tr("无法计算划键位置"));
            Point normal=chart.judgeNormal(event.getNote(),seconds,viewport);
            float x=bounds.x(p.getX()),y=bounds.y(p.getY());float[] target=bounds.flick(x,y,normal.getX()*viewport.getHeight()*.1f,normal.getY()*viewport.getHeight()*.1f);
            float progress=Math.max(0,Math.min(1,(time-event.getStartMs())/60f));
            if(x!=p.getX()||y!=p.getY())bounded[0]=true;
            p=new Point(x+(target[0]-x)*progress,y+(target[1]-y)*progress);
        }else p=plan.point(chart,event,time,viewport,screen,bounded);
        float x=bounds.x(p.getX()),y=bounds.y(p.getY());if(x!=p.getX()||y!=p.getY())bounded[0]=true;
        return escape(chart,event.getNote(),time/1000.0,viewport,new Point(x,y),previous,previousTime,offline,pureHold,!plan.hasFlickPath(event));
    }
}
