package android.view.accessibility;
public class AccessibilityEvent {public static final int TYPE_WINDOW_STATE_CHANGED=32,TYPE_WINDOWS_CHANGED=4194304;public int type;public String pkg,cls;public AccessibilityEvent(int t,String p,String c){type=t;pkg=p;cls=c;}public int getEventType(){return type;}public CharSequence getPackageName(){return pkg;}public CharSequence getClassName(){return cls;}}
