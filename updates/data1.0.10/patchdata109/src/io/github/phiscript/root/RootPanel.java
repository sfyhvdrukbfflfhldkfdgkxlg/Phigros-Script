package io.github.phiscript.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.assets.ChartLocation;
import io.github.phiscript.assets.ChartSource;
import io.github.phiscript.assets.RootChartSource;
import io.github.phiscript.engine.Chart;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Shared task/permission operations used by the unified native home screen. */
public final class RootPanel {
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final AtomicBoolean preflight=new AtomicBoolean();
    private static volatile boolean rootPending;
    private RootPanel(){}
    public static boolean isBusy(){return busy.get()||ManualChartControl.isOpen()||RootPhiraImport.isBusy()||PhiraImportControl.isBusy();}
    public static boolean isRootPending(){return rootPending;}
    public static void attach(Activity activity){
        AppSettings prefs=new AppSettings(activity);
        if("PHIGROS".equals(prefs.text("mode",""))){prefs.save("mode","VISUAL");prefs.saveFlag("phigros_manual_mode",false);prefs.saveFlag("phigros_auto_mode",true);}
        if(prefs.text("mode","").isEmpty()){prefs.save("mode","VISUAL");prefs.saveFlag("phigros_auto_mode",true);}
        InputBackend.rootEnabled=prefs.flag("root_input_enabled",false);
        HomeScreen.attach(activity);
        if(preflight.compareAndSet(false,true))activity.getWindow().getDecorView().post(()->{
            if(!alive(activity))return;
            Runnable access=()->{if(alive(activity)&&!accessibilityEnabled(activity)){
                try{activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
                catch(Exception e){RuntimeState.INSTANCE.log("请在系统设置启用无障碍："+e.getMessage());}
            }};
            // Request these in sequence: opening Settings during a su dialog steals focus.
            if(!InputBackend.rootEnabled)requestRoot(activity,access);else access.run();
        });
    }
    public static boolean accessibilityEnabled(Activity activity){
        try{
            String enabled=Settings.Secure.getString(activity.getContentResolver(),"enabled_accessibility_services");
            ComponentName expected=new ComponentName(activity,io.github.phiscript.input.TouchService.class);
            if(enabled!=null)for(String value:enabled.split(":"))if(expected.equals(ComponentName.unflattenFromString(value)))return true;
        }catch(Exception ignored){}
        return false;
    }
    public static void requestRoot(Activity activity){requestRoot(activity,null);}
    private static void requestRoot(Activity activity,Runnable after){
        if(blocked(activity)||!busy.compareAndSet(false,true))return;
        rootPending=true;HomeScreen.refresh(activity);
        RuntimeState.INSTANCE.log("正在请求 Root 授权；授权完成后触控后端会自动就绪");
        new Thread(()->{
            String error=null;
            try{
                android.util.DisplayMetrics m=new android.util.DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getRealMetrics(m);
                try(RootClient client=new RootClient(activity.getApplicationContext(),m.widthPixels,m.heightPixels)){
                    client.frame(new ArrayList<RootTouchState.Sample>());
                }
                InputBackend.rootEnabled=true;new AppSettings(activity).saveFlag("root_input_enabled",true);
                RuntimeState.INSTANCE.log("Root 已授权，触控后端就绪");
            }catch(Exception e){error=e.getMessage();RuntimeState.INSTANCE.log("Root 未启用："+error);}
            final String failure=error;rootPending=false;busy.set(false);
            activity.runOnUiThread(()->{
                if(!alive(activity))return;HomeScreen.refresh(activity);
                if(failure!=null)message(activity,"Root 未启用",failure);
                if(after!=null)after.run();
            });
        },"phi-root-preflight").start();
    }
    public static void useAccessibility(Activity a){
        if(blocked(a))return;InputBackend.rootEnabled=false;new AppSettings(a).saveFlag("root_input_enabled",false);HomeScreen.refresh(a);
    }
    static boolean alive(Activity a){return!a.isFinishing()&&!a.isDestroyed();}
    static void message(Activity a,String title,String text){if(alive(a))new AlertDialog.Builder(a).setTitle(io.github.phiscript.i18n.UiText.text(title)).setMessage(io.github.phiscript.i18n.UiText.text(text)).setPositiveButton(io.github.phiscript.i18n.UiText.text("确定"),null).show();}
    public static boolean blocked(Activity a){
        boolean originalBusy=false;try{Method m=a.getClass().getDeclaredMethod("taskBusy");m.setAccessible(true);originalBusy=Boolean.TRUE.equals(m.invoke(a));}catch(Exception ignored){}
        if(originalBusy||busy.get()||RuntimeState.INSTANCE.getRunning().get()||ManualChartControl.isOpen()){
            message(a,"请先停止当前任务","请停止当前导入、识别或演奏，再切换模式或谱库。");return true;
        }return false;
    }
    public static void scan(Activity activity){
        if(blocked(activity)||!busy.compareAndSet(false,true))return;
        RuntimeState.INSTANCE.log("Root 正在导入已安装 Phigros 的 APK 资源索引…");
        new Thread(()->{
            String error=null;ChartLibrary result=null;
            try{
                ChartSource source=new RootChartSource(activity);
                Method method=ChartLibrary.Companion.class.getDeclaredMethod("scanSource",Context.class,ChartSource.class,String.class,Function1.class);
                method.setAccessible(true);
                Function1<String,Unit> progress=s->{RuntimeState.INSTANCE.log(s);return Unit.INSTANCE;};
                result=(ChartLibrary)method.invoke(ChartLibrary.Companion,activity.getApplicationContext(),source,"Root · 已安装 Phigros",progress);
            }catch(Exception e){Throwable cause=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;error=cause.getMessage();}
            if(result!=null)RuntimeState.INSTANCE.setLibrary(result);
            final ChartLibrary library=result;final String failure=error;busy.set(false);
            activity.runOnUiThread(()->{
                if(library==null){message(activity,"Root 导入失败",failure==null?"未能读取游戏资源":failure);return;}
                new AppSettings(activity).saveFlag("root_phigros_source",true);
                message(activity,"Phigros 谱库已导入","已发现 "+library.getLocations().size()+" 个谱面难度。点击首页“开始打谱”自动检测。游戏更新后请重新导入；重启应用后若谱库未恢复，也可再次导入。\n只读取 APK；不修改游戏文件。未识别的新版资源格式会报错，不会假装导入成功。");
            });
        },"phi-root-import").start();
    }
    public static void choose(Activity activity){
        if(blocked(activity))return;
        new AppSettings(activity).saveFlag("phigros_auto_mode",false);
        new AppSettings(activity).save("mode","VISUAL");new AppSettings(activity).saveFlag("phigros_manual_mode",true);
        ChartLibrary library=RuntimeState.INSTANCE.getLibrary();
        if(library==null){message(activity,"尚无 Phigros 谱库","请先点击 Root 一键导入，或使用原有的扫描 / 手动导入 APK 按钮。");return;}
        EditText query=new EditText(activity);query.setSingleLine(true);query.setHint("歌曲 ID 或难度，留空列出全部");
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("筛选 Phigros 谱面")).setView(query).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).setPositiveButton(io.github.phiscript.i18n.UiText.text("查找"),(d,w)->{
            String text=query.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            List<ChartLocation> entries=new ArrayList<>();for(ChartLocation l:library.getLocations())
                if((l.getSongId()+" "+l.getDifficulty()).toLowerCase(java.util.Locale.ROOT).contains(text))entries.add(l);
            Collections.sort(entries,(a,b)->{int song=a.getSongId().compareTo(b.getSongId());return song!=0?song:a.getDifficulty().compareTo(b.getDifficulty());});
            if(entries.isEmpty()){message(activity,"没有匹配谱面","请换一个歌曲 ID 或清空筛选。");return;}
            String[] labels=new String[entries.size()];for(int i=0;i<labels.length;i++)labels[i]=entries.get(i).getSongId()+" / "+entries.get(i).getDifficulty();
            new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("选择与游戏一致的歌曲 / 难度")).setItems(io.github.phiscript.i18n.UiText.text(labels),(dialog,index)->load(activity,library,entries.get(index))).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).show();
        }).show();
    }
    private static void load(Activity activity,ChartLibrary library,ChartLocation location){
        if(blocked(activity)||!busy.compareAndSet(false,true))return;
        RuntimeState.INSTANCE.log("正在读取 "+location.getSongId()+" / "+location.getDifficulty());
        new Thread(()->{
            Chart chart=null;String error=null;
            try{chart=library.load(location.getSongId(),location.getDifficulty());io.github.phiscript.assets.ChartTimingIndex.ensureForPlayback(activity,library,location.getSongId(),location.getDifficulty(),chart);io.github.phiscript.input.GesturePlan.preflight(chart);}
            catch(Exception e){error=e.getMessage();}
            final Chart result=chart;final String failure=error;busy.set(false);
            activity.runOnUiThread(()->{
                if(result==null){message(activity,"读取谱面失败",failure);return;}
                if(!alive(activity))return;
                new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("谱面已读取："+result.getNotes().size()+" 个音符"))
                    .setMessage(io.github.phiscript.i18n.UiText.text("手动同步模式：先在游戏中进入所选歌曲，再点控制条“开始”。谱面零点按设置的延迟计算，需自行对齐游戏起始；暂停、重试或切歌后必须停止并重新开始。\n\n演奏时控制条隐藏，音量减键和通知栏停止。Root 后端也需要无障碍服务进行前台保护。"))
                    .setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).setPositiveButton(io.github.phiscript.i18n.UiText.text("打开控制条"),(d,w)->{
                        try{io.github.phiscript.record.RecordingConsentActivity.open(activity,result,location.getSongId()+" / "+location.getDifficulty());}
                        catch(Exception e){message(activity,"无法开始",e.getMessage());}
                    }).show();
            });
        },"phi-chart-load").start();
    }
    public static void timing(Activity a){
        if(blocked(a))return;AppSettings prefs=new AppSettings(a);
        LinearLayout form=new LinearLayout(a);form.setOrientation(1);form.setPadding(28,8,28,8);
        TextView explanation=new TextView(a);explanation.setText(io.github.phiscript.i18n.UiText.text("点击控制条“开始”到谱面零点的延迟（0–15000 ms），正数表示稍后开始。需自行对齐游戏，不会自动识别重试。\n\n视口沿用原设置中的归一化玩法区域；为适配宽屏，默认居中限制为 16:9。关闭限制时使用完整配置视口。"));form.addView(explanation);
        EditText delay=new EditText(a);delay.setInputType(InputType.TYPE_CLASS_NUMBER);delay.setText(io.github.phiscript.i18n.UiText.text(prefs.text("phigros_manual_delay_ms","1000")));form.addView(delay);
        android.widget.CheckBox aspect=new android.widget.CheckBox(a);aspect.setText(io.github.phiscript.i18n.UiText.text("视口居中限制 16:9"));aspect.setChecked(prefs.flag("phigros_manual_16_9",true));form.addView(aspect);
        new AlertDialog.Builder(a).setTitle(io.github.phiscript.i18n.UiText.text("Phigros 手动同步")).setView(form).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).setPositiveButton(io.github.phiscript.i18n.UiText.text("保存"),(d,w)->{
            try{int value=Integer.parseInt(delay.getText().toString());if(value<0||value>15000)throw new NumberFormatException();
                prefs.save("phigros_manual_delay_ms",String.valueOf(value));prefs.saveFlag("phigros_manual_16_9",aspect.isChecked());}
            catch(NumberFormatException e){message(a,"参数无效","延迟应为 0–15000 的整数。");}
        }).show();
    }
}
