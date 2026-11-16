package io.github.phiscript.input;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Live window evidence for the native TouchService foreground getter. Its event
 * cache can be stale after the projection permission sheet or our own overlay. */
public final class GameForeground {
    private static final long CACHE_MS=35;
    private static final Map<TouchService,Snapshot> CACHE=new WeakHashMap<>();
    private static final class Snapshot {String event,target;long at;boolean foreground;}
    private GameForeground(){}
    public static boolean isForeground(TouchService service){
        if(service==null)return false;
        String target=service.getTargetPackage(),event=service.getForegroundPackage();
        if(target==null||target.isEmpty())return false;
        long now=SystemClock.uptimeMillis();
        synchronized(CACHE){
            Snapshot old=CACHE.get(service);
            if(old!=null&&now>=old.at&&now-old.at<CACHE_MS&&same(old.event,event)&&same(old.target,target))return old.foreground;
        }
        String current=focusedPackage(service);
        // Missing window metadata is not new evidence of a switch. Keep only a
        // positive target-package event; never assume our activity is the game.
        boolean foreground=current!=null?target.equals(current):target.equals(event);
        Snapshot result=new Snapshot();result.event=event;result.target=target;result.at=now;result.foreground=foreground;
        synchronized(CACHE){CACHE.put(service,result);}
        return foreground;
    }
    private static boolean same(String a,String b){return a==b||a!=null&&a.equals(b);}
    private static String packageOf(AccessibilityWindowInfo window){
        AccessibilityNodeInfo root=window.getRoot();if(root==null)return null;
        try{CharSequence p=root.getPackageName();return p==null?null:p.toString();}
        finally{root.recycle();}
    }
    private static String focusedPackage(TouchService service){
        try{
            List<AccessibilityWindowInfo> windows=service.getWindows();
            boolean ownOverlay=false,focusedUnknown=false;
            if(windows!=null){
                for(AccessibilityWindowInfo window:windows){
                    if(!window.isFocused())continue;
                    if(window.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY){
                        String owner=packageOf(window);
                        if(service.getPackageName().equals(owner)){ownOverlay=true;continue;}
                        return "";
                    }
                    if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)return "";
                    String owner=packageOf(window);
                    if(owner!=null&&!owner.isEmpty())return owner;
                    focusedUnknown=true;
                }
                if(ownOverlay&&!focusedUnknown){
                    // Some OEMs assign focus to the accessibility control. Only
                    // the active application underneath can establish the game.
                    for(AccessibilityWindowInfo window:windows)
                        if(window.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&window.isActive()){
                            String owner=packageOf(window);
                            if(owner!=null&&!owner.isEmpty())return owner;
                        }
                }
            }
            AccessibilityNodeInfo root=service.getRootInActiveWindow();
            if(root!=null)try{
                CharSequence p=root.getPackageName();String owner=p==null?null:p.toString();
                // Our overlay root alone is not evidence of our main activity.
                if(owner!=null&&(!owner.equals(service.getPackageName())||!ownOverlay))return owner;
            }finally{root.recycle();}
            if(ownOverlay||focusedUnknown)return "";
        }catch(RuntimeException ignored){/* Keep only the last actual target event. */}
        return null;
    }
}
