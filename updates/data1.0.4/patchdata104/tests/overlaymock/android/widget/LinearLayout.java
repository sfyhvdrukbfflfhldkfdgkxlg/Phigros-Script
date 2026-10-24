package android.widget;
import android.view.ViewGroup;
public final class LinearLayout extends ViewGroup {public static final int HORIZONTAL=0;public int orientation;public void setOrientation(int i){orientation=i;}public void setGravity(int i){}public static class LayoutParams extends ViewGroup.LayoutParams {public float weight;public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,float f){super(w,h);weight=f;}}}
