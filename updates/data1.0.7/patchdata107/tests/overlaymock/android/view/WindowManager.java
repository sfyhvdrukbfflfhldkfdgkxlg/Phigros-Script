package android.view;
public interface WindowManager {Display getDefaultDisplay();void updateViewLayout(View v,ViewGroup.LayoutParams p);public static final class LayoutParams extends ViewGroup.LayoutParams {public int gravity,x,y;public LayoutParams(int w,int h){super(w,h);}}}
