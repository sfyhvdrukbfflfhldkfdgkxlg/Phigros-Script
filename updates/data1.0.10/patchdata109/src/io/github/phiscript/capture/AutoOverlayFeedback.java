package io.github.phiscript.capture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.phiscript.RuntimeState;
import java.lang.reflect.Field;

/** The existing overlay owns masking and stop; only preparation UI layout/lifecycle changes. */
public final class AutoOverlayFeedback {
    private final RecognitionOverlay overlay;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object lifecycle=new Object();
    private volatile boolean playing,closed;
    public AutoOverlayFeedback(Context context,RecognitionOverlay overlay){this.context=context;this.overlay=overlay;}
    public void show(String text){
        render(text,false);
    }
    private void render(String text,boolean duringPlay){
        if((playing&&!duringPlay)||closed)return;
        main.post(()->{
            synchronized(lifecycle){
                if((playing&&!duringPlay)||closed)return;
                try{
                    LinearLayout panel=(LinearLayout)field("panel");TextView label=(TextView)field("label");
                    if(panel!=null&&label!=null){label.setText(io.github.phiscript.i18n.UiText.text(text));compact();return;}
                }catch(Exception ignored){}
                overlay.status(text);
                // status() posts to this same Looper; layout runs after panel creation.
                main.post(()->{if((!playing||duringPlay)&&!closed)compact();});
            }
        });
    }
    public void playing(){synchronized(lifecycle){playing=true;}render("正在打谱 · 音量减可停止",true);}
    public void waiting(String text){synchronized(lifecycle){if(closed)return;playing=false;}show(text);}
    public void close(){synchronized(lifecycle){closed=true;}try{overlay.hide();}catch(RuntimeException e){RuntimeState.INSTANCE.log("旧控制窗已失效，等待服务恢复："+e.getMessage());}}
    private Object field(String name)throws Exception{Field f=RecognitionOverlay.class.getDeclaredField(name);f.setAccessible(true);return f.get(overlay);}
    private void compact(){
        try{
            LinearLayout panel=(LinearLayout)field("panel");TextView label=(TextView)field("label");
            if(panel==null||label==null)return;
            WindowManager manager=(WindowManager)field("manager");
            DisplayMetrics metrics=new DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(metrics);
            AutoOverlayBounds size=new AutoOverlayBounds(metrics.widthPixels,metrics.heightPixels,metrics.density);
            panel.setOrientation(LinearLayout.HORIZONTAL);panel.setGravity(Gravity.CENTER_VERTICAL);
            panel.setPadding(size.padding,size.padding,size.padding,size.padding);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,size.textSize);label.setMaxLines(3);label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(0,0,size.padding,0);label.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            if(panel.getChildCount()>1&&panel.getChildAt(1) instanceof LinearLayout){
                LinearLayout actions=(LinearLayout)panel.getChildAt(1);actions.setLayoutParams(new LinearLayout.LayoutParams(size.actionWidth,-1));actions.setGravity(Gravity.CENTER_VERTICAL);
                for(int i=0;i<actions.getChildCount();i++){
                    View child=actions.getChildAt(i);if(child.getVisibility()==View.GONE)continue;
                    child.setLayoutParams(new LinearLayout.LayoutParams(-1,-1));child.setPadding(0,0,0,0);child.setMinimumHeight(0);child.setMinimumWidth(0);
                    if(child instanceof Button){Button stop=(Button)child;stop.setMinHeight(0);stop.setMinWidth(0);stop.setTextSize(TypedValue.COMPLEX_UNIT_PX,size.textSize);stop.setContentDescription(io.github.phiscript.i18n.UiText.text("停止自动检测和打谱"));}
                }
            }
            WindowManager.LayoutParams params=(WindowManager.LayoutParams)panel.getLayoutParams();
            params.width=size.width;params.height=size.height;params.gravity=Gravity.TOP|Gravity.RIGHT;params.x=size.margin;params.y=size.margin;
            manager.updateViewLayout(panel,params);
        }catch(Exception e){RuntimeState.INSTANCE.log("状态窗布局调整失败："+e.getMessage());}
    }
}
