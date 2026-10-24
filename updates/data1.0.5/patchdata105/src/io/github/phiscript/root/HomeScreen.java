package io.github.phiscript.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.assets.PhiraLibrary;
import io.github.phiscript.input.TouchService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** One native home, one transport action. Reuses the Activity's live status/log fields. */
public final class HomeScreen {
    private static final int BG=0xff0c1420,CARD=0xff172331,INK=0xfff1f6fa,MUTED=0xff9cabbc,ACCENT=0xff79e6c5;
    private final Activity activity;
    private final LinearLayout root;
    private TextView badge,summary,library,permission,mode;
    private Button primary;
    private Switch recording;
    private final Runnable ticker=new Runnable(){public void run(){if(RootPanel.alive(activity)&&root.isAttachedToWindow()){update();root.postDelayed(this,700);}}};
    private HomeScreen(Activity activity,LinearLayout root){this.activity=activity;this.root=root;}
    public static void attach(Activity a){
        io.github.phiscript.i18n.UiText.attach(a);
        LinearLayout root=find(a.findViewById(android.R.id.content));if(root==null||root.getTag() instanceof HomeScreen)return;
        HomeScreen home=new HomeScreen(a,root);root.setTag(home);home.build();
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){root.removeCallbacks(home.ticker);root.post(home.ticker);}
            public void onViewDetachedFromWindow(View v){root.removeCallbacks(home.ticker);}
        });
        if(root.isAttachedToWindow())root.post(home.ticker);
    }
    public static void refresh(Activity a){LinearLayout root=find(a.findViewById(android.R.id.content));if(root!=null&&root.getTag() instanceof HomeScreen)((HomeScreen)root.getTag()).update();}
    private static LinearLayout find(View v){
        if(v instanceof LinearLayout&&((LinearLayout)v).getOrientation()==LinearLayout.VERTICAL)return(LinearLayout)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){LinearLayout r=find(g.getChildAt(i));if(r!=null)return r;}}
        return null;
    }
    private void build(){
        TextView oldStatus=field("statusView"),oldAccess=field("accessibilityView"),oldLog=field("logView");
        root.removeAllViews();root.setPadding(dp(20),dp(22),dp(20),dp(30));root.setBackgroundColor(BG);
        if(root.getParent() instanceof View)((View)root.getParent()).setBackgroundColor(BG);
        activity.getWindow().setStatusBarColor(BG);activity.getWindow().setNavigationBarColor(BG);
        View decor=activity.getWindow().getDecorView();decor.setSystemUiVisibility(decor.getSystemUiVisibility()&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));

        TextView eyebrow=text(root,"PHIGROS SCRIPT",12,ACCENT,true);eyebrow.setLetterSpacing(.18f);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(heading,params(-1,-2,8));
        TextView title=text(heading,"演奏台",30,INK,true);title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        TextView version=text(heading,"data1.0.5",12,MUTED,false);version.setPadding(dp(12),dp(7),dp(12),dp(7));version.setBackground(shape(CARD,12));
        text(root,"识别谱面 · 同步起点 · 自动演奏",13,MUTED,false).setPadding(0,dp(7),0,dp(20));

        LinearLayout transport=card();
        badge=text(transport,"准备就绪",12,ACCENT,true);
        mode=text(transport,"",21,INK,true);mode.setPadding(0,dp(12),0,dp(6));mode.setCompoundDrawablePadding(dp(8));
        mode.setContentDescription(io.github.phiscript.i18n.UiText.text("演奏模式，点击切换"));mode.setMinHeight(dp(48));mode.setGravity(Gravity.CENTER_VERTICAL);
        mode.setOnClickListener(v->PlayEntry.choose(activity));mode.setFocusable(true);
        summary=text(transport,"",13,MUTED,false);summary.setMinHeight(dp(48));
        primary=action(transport,"开始打谱",true,()->{
            if(RootPhiraImport.isBusy()||PhiraImportControl.isBusy()){RootPhiraImport.cancel();PhiraImportControl.cancel();}
            else if(RuntimeState.INSTANCE.getRunning().get()||ManualChartControl.isOpen())AutoEntry.stop(activity);
            else PlayEntry.start(activity,false);
            update();
        });primary.setTag("data-primary-transport");
        text(transport,"演奏时保持游戏横屏，音量减键可停止",11,MUTED,false).setPadding(0,dp(12),0,0);

        LinearLayout charts=card();label(charts,"曲库", "CHART LIBRARY");
        library=text(charts,"",14,INK,false);library.setPadding(0,dp(12),0,dp(4));
        action(charts,"导入谱面",false,this::importCharts).setTag("data-import");

        LinearLayout controls=card();label(controls,"权限与触控", "INPUT");
        permission=text(controls,"",13,MUTED,false);permission.setPadding(0,dp(10),0,dp(4));
        action(controls,"管理权限与触控方式",false,this::permissions);

        LinearLayout video=card();label(video,"录屏", "RECORDING");
        recording=new Switch(activity);recording.setText(io.github.phiscript.i18n.UiText.text("打谱时自动录屏"));recording.setTextSize(15);recording.setTextColor(INK);recording.setMinHeight(dp(54));
        recording.setThumbTintList(ColorStateList.valueOf(ACCENT));recording.setTrackTintList(ColorStateList.valueOf(0xff365b58));
        recording.setChecked(new AppSettings(activity).flag("recording_on_play",true));video.addView(recording,new LinearLayout.LayoutParams(-1,-2));
        recording.setOnCheckedChangeListener((v,enabled)->{if(!RuntimeState.INSTANCE.getRunning().get()&&!RootPanel.isBusy())new AppSettings(activity).saveFlag("recording_on_play",enabled);});
        text(video,"演奏开始后录制无声 MP4，结束时保存",12,MUTED,false);
        action(video,"查看最近录屏",false,()->{
            try{activity.startActivity(io.github.phiscript.record.RecordingFiles.view(activity));}
            catch(Exception e){RootPanel.message(activity,"录屏位置","Android 10+：Movies/PhigrosScript；Android 8/9：应用录屏目录。\n\n"+e.getMessage());}
        });

        action(root,"设置与工具",false,this::settings);
        LinearLayout diagnostics=card();
        LinearLayout details=new LinearLayout(activity);details.setOrientation(1);details.setVisibility(View.GONE);
        TextView expand=text(diagnostics,"运行记录  ＋",14,MUTED,true);expand.setMinHeight(dp(48));expand.setGravity(Gravity.CENTER_VERTICAL);expand.setFocusable(true);
        expand.setOnClickListener(v->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(io.github.phiscript.i18n.UiText.text(open?"运行记录  −":"运行记录  ＋"));});
        diagnostics.addView(details,new LinearLayout.LayoutParams(-1,-2));
        for(TextView view:new TextView[]{oldStatus,oldAccess,oldLog}){
            if(view==null)continue;view.setTextColor(MUTED);view.setTextSize(12);view.setPadding(0,dp(8),0,dp(8));details.addView(view,new LinearLayout.LayoutParams(-1,-2));
        }
        text(root,"Phigros Script  /  data1.0.5",11,MUTED,false).setPadding(0,dp(20),0,0);
        update();
    }
    private void update(){
        boolean running=RuntimeState.INSTANCE.getRunning().get()||ManualChartControl.isOpen(),busy=RootPanel.isBusy();
        boolean touch=RuntimeState.INSTANCE.getActiveTouchStop().get()!=null;
        AppSettings prefs=new AppSettings(activity);
        mode.setText(io.github.phiscript.i18n.UiText.text(PlayEntry.label(activity)+"  ⌄"));
        badge.setText(io.github.phiscript.i18n.UiText.text(RootPanel.isRootPending()?"正在等待 ROOT 授权":running?(touch?"正在演奏":"自动检测已开启"):busy?"正在处理谱库":"待机"));
        String log=RuntimeState.INSTANCE.logText();
        if((running||RootPhiraImport.isBusy()||PhiraImportControl.isBusy())&&log!=null&&!log.trim().isEmpty()){
            String value=log.trim();int line=value.lastIndexOf('\n');if(line>=0)value=value.substring(line+1);summary.setText(io.github.phiscript.i18n.UiText.text(value.length()>120?value.substring(0,120)+"…":value));
        }else if(!running&&!busy&&!prefs.text("phigros_last_error","").isEmpty()){
            badge.setText(io.github.phiscript.i18n.UiText.text("上次自动检测已停止"));summary.setText(io.github.phiscript.i18n.UiText.text(prefs.text("phigros_last_error","")));
        }else summary.setText(io.github.phiscript.i18n.UiText.text(RootPanel.isRootPending()?"授权结束后会自动更新状态，返回游戏后继续确认曲目。":
            prefs.flag("phigros_auto_mode",false)?"进入歌曲画面后自动暂停，再识别谱面并点击中央重试。":"选择与游戏一致的模式，导入谱面后即可开始。"));
        boolean importing=RootPhiraImport.isBusy()||PhiraImportControl.isBusy(),cancelling=RootPhiraImport.isCancelling()||PhiraImportControl.isCancelling();
        primary.setText(io.github.phiscript.i18n.UiText.text(cancelling?"正在取消 Phira 导入…":importing?"取消 Phira 导入":running?"停止打谱":RootPanel.isRootPending()?"等待 Root 授权…":busy?"正在处理…":"开始打谱"));
        primary.setEnabled(!cancelling&&(importing||running||!busy));primary.setAlpha(primary.isEnabled()?1f:.5f);
        primary.setBackground(ripple(running?0xff5b3340:ACCENT));primary.setTextColor(running?INK:BG);
        ChartLibrary phi=RuntimeState.INSTANCE.getLibrary();PhiraLibrary phira=RuntimeState.INSTANCE.getPhiraLibrary();
        library.setText(io.github.phiscript.i18n.UiText.text("Phigros  "+(phi==null?"未导入":phi.getLocations().size()+" 个谱面难度")+"\nPhira  "+(phira==null?"未导入":phira.getEntries().size()+" 个谱面")));
        permission.setText(io.github.phiscript.i18n.UiText.text((InputBackend.rootEnabled?"Root 触控":"无障碍触控")+"  ·  "+(TouchService.Companion.getCurrent()!=null?"无障碍已连接":"无障碍未连接")+"\n"+(Settings.canDrawOverlays(activity)?"悬浮窗已允许":"悬浮窗待授权")+"  ·  截屏权限在开始时申请"));
        recording.setEnabled(!running&&!busy);boolean enabled=prefs.flag("recording_on_play",true);if(recording.isChecked()!=enabled)recording.setChecked(enabled);
    }
    private void importCharts(){
        if(RootPanel.blocked(activity))return;
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("导入谱面")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{"Phigros · Root 一键导入","Phigros · 选择 APK 文件","Phira / RPE · Root 或文件导入","Phigros · 扫描已安装 APK"}),(d,i)->{
            if(i==0)RootPanel.scan(activity);
            else if(i==1)invoke(activity,"chooseApks");
            else if(i==2)invoke(activity,"choosePhira");
            else invoke(activity,"scan",new Class<?>[]{kotlin.jvm.functions.Function0.class},new Object[]{null});
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).show();
    }
    private void permissions(){
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("权限与触控")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{"选择触控方式：Root / 无障碍","启用无障碍服务","允许悬浮窗","Shizuku 辅助与诊断"}),(d,i)->{
            if(i==0){
                if(RootPanel.blocked(activity))return;
                new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("触控方式")).setSingleChoiceItems(io.github.phiscript.i18n.UiText.text(new String[]{"Root（需授权）","无障碍"}),InputBackend.rootEnabled?0:1,(dialog,index)->{
                    dialog.dismiss();if(index==0){if(!InputBackend.rootEnabled)RootPanel.requestRoot(activity);}else RootPanel.useAccessibility(activity);
                }).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).show();
            }else if(i==1)activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            else if(i==2)activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+activity.getPackageName())));
            else shizuku();
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("关闭"),null).show();
    }
    private void settings(){
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("设置与工具")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{"Phigros 自动同步 / 暂停识别","识别区域、暂停/重试与 Phira 延迟","修正曲名识别","Phira · 自动识别 / 选择谱面","Phigros · 手动选曲（备用）","手动起点与视口","只识别预览","无障碍诊断与 Shizuku","RPE 兼容说明","连键手势与边缘避让","语言 / Language","检查更新"}),(d,i)->{
            switch(i){
                case 0:AutoEntry.settings(activity);break;
                case 1:invoke(activity,"calibration");break;
                case 2:invoke(activity,"editAlias");break;
                case 3:if(!RootPanel.blocked(activity)){PlayEntry.select(activity,1);invoke(activity,"choosePhiraChart");}break;
                case 4:RootPanel.choose(activity);break;
                case 5:RootPanel.timing(activity);break;
                case 6:PlayEntry.start(activity,true);break;
                case 7:shizuku();break;
                case 8:RootPanel.message(activity,"RPE 兼容说明","支持 JSON / ZIP / PEZ、长键多押、父子线、变 BPM、上下侧及已适配的多层事件。这里只计算触控，不渲染视频或特效；自定义脚本、带音符的 UI 绑定线及新版扩展仍有限制。不会保证所有谱面兼容或全连。");break;
                case 9:GestureSettings.open(activity);break;
                case 10:io.github.phiscript.i18n.LanguageSettings.open(activity);break;
                case 11:io.github.phiscript.update.UpdateChecker.check(activity);break;
            }
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("关闭"),null).show();
    }
    private void shizuku(){
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("无障碍与 Shizuku")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{"连接 Shizuku","通过 Shizuku 启用无障碍","查看 / 复制无障碍诊断","记录服务关闭原因"}),(d,i)->{
            if(i==0){try{io.github.phiscript.assets.ApkAccess.INSTANCE.requestPermission();}catch(Exception e){RootPanel.message(activity,"Shizuku",e.getMessage());}}
            else invoke(activity,i==1?"enableAccessibilityWithShizuku":i==2?"accessibilityHelp":"captureAccessibilityFailure");
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("关闭"),null).show();
    }
    public static void invoke(Activity a,String name){invoke(a,name,new Class<?>[0],new Object[0]);}
    private static void invoke(Activity a,String name,Class<?>[] types,Object[] values){
        try{Method m=a.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,values);}
        catch(Exception e){Throwable t=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;RootPanel.message(a,"操作未完成",String.valueOf(t.getMessage()));}
    }
    private TextView field(String name){try{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return(TextView)f.get(activity);}catch(Exception e){RuntimeState.INSTANCE.log("界面状态字段未读取："+name);return null;}}
    private LinearLayout card(){LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(dp(18),dp(17),dp(18),dp(17));box.setBackground(shape(CARD,20));root.addView(box,params(-1,-2,12));return box;}
    private void label(LinearLayout box,String title,String caption){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);box.addView(row);TextView name=text(row,title,16,INK,true);name.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));text(row,caption,10,MUTED,false).setLetterSpacing(.08f);}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView view=new TextView(activity);view.setText(io.github.phiscript.i18n.UiText.text(value));view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(3),1f);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(view,new LinearLayout.LayoutParams(-2,-2));return view;}
    private Button action(LinearLayout parent,String value,boolean main,Runnable click){Button b=new Button(activity);b.setText(io.github.phiscript.i18n.UiText.text(value));b.setAllCaps(false);b.setTextColor(main?BG:INK);b.setTextSize(main?18:14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(main?58:48));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(ripple(main?ACCENT:0xff223347));parent.addView(b,params(-1,-2,12));b.setOnClickListener(v->click.run());return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private RippleDrawable ripple(int color){return new RippleDrawable(ColorStateList.valueOf(0x3379e6c5),shape(color,13),null);}
    private LinearLayout.LayoutParams params(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
