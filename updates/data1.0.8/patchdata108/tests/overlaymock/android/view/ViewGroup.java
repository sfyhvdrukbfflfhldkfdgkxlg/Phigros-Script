package android.view;
import java.util.*;
public class ViewGroup extends View {public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}protected final List<View> children=new ArrayList<>();public void addView(View v){children.add(v);}public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}
