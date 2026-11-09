package android.view;
public final class FakeWindowManager implements WindowManager {public final Display display=new Display();public int updates;public Display getDefaultDisplay(){return display;}public void updateViewLayout(View v,ViewGroup.LayoutParams p){updates++;v.setLayoutParams(p);}}
