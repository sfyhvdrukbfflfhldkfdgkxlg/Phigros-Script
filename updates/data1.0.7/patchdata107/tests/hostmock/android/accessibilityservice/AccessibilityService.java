package android.accessibilityservice;
public class AccessibilityService extends android.content.Context {public java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows=new java.util.ArrayList<>();public boolean fail;public java.util.List<android.view.accessibility.AccessibilityWindowInfo> getWindows(){if(fail)throw new IllegalStateException();return windows;}}
