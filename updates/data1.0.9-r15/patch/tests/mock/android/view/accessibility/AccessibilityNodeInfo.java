package android.view.accessibility;
public class AccessibilityNodeInfo {
 public final String pkg;public boolean recycled;
 public AccessibilityNodeInfo(String pkg){this.pkg=pkg;}
 public CharSequence getPackageName(){return pkg;}
 public void recycle(){recycled=true;}
}
