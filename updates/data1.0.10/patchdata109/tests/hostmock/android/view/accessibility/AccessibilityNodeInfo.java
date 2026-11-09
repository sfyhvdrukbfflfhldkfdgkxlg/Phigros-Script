package android.view.accessibility;
public class AccessibilityNodeInfo {public String pkg;public boolean recycled;public AccessibilityNodeInfo(String p){pkg=p;}public CharSequence getPackageName(){return pkg;}public void recycle(){recycled=true;}}
