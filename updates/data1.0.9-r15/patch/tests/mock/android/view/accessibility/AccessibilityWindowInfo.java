package android.view.accessibility;
public class AccessibilityWindowInfo {
 public static final int TYPE_APPLICATION=1,TYPE_SYSTEM=3,TYPE_ACCESSIBILITY_OVERLAY=4;
 public int type;public boolean focused,active;public AccessibilityNodeInfo root;
 public AccessibilityWindowInfo(int type,boolean focused,boolean active,String pkg){this.type=type;this.focused=focused;this.active=active;root=pkg==null?null:new AccessibilityNodeInfo(pkg);}
 public int getType(){return type;}public boolean isFocused(){return focused;}public boolean isActive(){return active;}public AccessibilityNodeInfo getRoot(){return root;}
}
