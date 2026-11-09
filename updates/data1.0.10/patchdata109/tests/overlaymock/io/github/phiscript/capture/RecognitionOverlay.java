package io.github.phiscript.capture;
import android.os.*;import android.widget.*;import android.view.*;
public final class RecognitionOverlay {
 private LinearLayout panel;private TextView label;private WindowManager manager;public int nativeStatuses,hides,stops;private final Handler main=new Handler(Looper.getMainLooper());
 public void host(android.content.Context context){manager=context.window;}
 public void status(String s){nativeStatuses++;main.post(()->{if(panel==null){panel=new LinearLayout();label=new TextView();panel.addView(label);LinearLayout row=new LinearLayout();for(int i=0;i<2;i++){Button hidden=new Button();hidden.setVisibility(View.GONE);row.addView(hidden);}Button stop=new Button();stop.click=()->stops++;row.addView(stop);panel.addView(row);panel.setLayoutParams(new WindowManager.LayoutParams(430,-2));}label.setText(s);});}
 public long hide(){hides++;main.post(()->{panel=null;label=null;});Handler.drain();return 0;}
 public boolean visible(){return panel!=null;}public LinearLayout panel(){return panel;}public TextView label(){return label;}
}
