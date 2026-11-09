package io.github.phiscript.input;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/** Resolve focus without treating our non-focusable control window as leaving the game. */
public final class ForegroundEvents {
    private ForegroundEvents(){}
    public static String packageFor(AccessibilityService service,AccessibilityEvent event){
        if(event==null)return null;
        int type=event.getEventType();
        if(type==AccessibilityEvent.TYPE_WINDOWS_CHANGED)return focused(service);
        if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return null;
        CharSequence pkg=event.getPackageName();if(pkg==null)return focused(service);
        String reported=pkg.toString();
        if(reported.equals(service.getPackageName())){
            CharSequence cls=event.getClassName();
            if(cls!=null&&cls.toString().equals("io.github.phiscript.MainActivity"))return reported;
            // An overlay's state event carries our package even while Unity is focused.
            // Unknown focus must retain the last application, not cancel it as our UI.
            return focused(service);
        }
        return reported.isEmpty()?null:reported;
    }
    private static String focused(AccessibilityService service){
        try{
            List<AccessibilityWindowInfo> windows=service.getWindows();if(windows==null)return null;
            for(AccessibilityWindowInfo window:windows){
                if(!window.isFocused())continue;
                if(window.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)continue;
                // A focused system/permission window blocks game input immediately.
                if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)return "";
                AccessibilityNodeInfo root=window.getRoot();if(root==null)return null;
                try{CharSequence pkg=root.getPackageName();return pkg==null?null:pkg.toString();}finally{root.recycle();}
            }
        }catch(RuntimeException ignored){}
        return null;
    }
}
