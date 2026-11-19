package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.ExternalCharts;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.assets.ChartLocation;
import io.github.phiscript.assets.PhiraEntry;
import io.github.phiscript.assets.PhiraLibrary;
import io.github.phiscript.input.PreparedChartStore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

public final class PreparedChartsUi {
    private static final AtomicBoolean busy=new AtomicBoolean();
    public static boolean isBusy(){return busy.get();}
    private PreparedChartsUi(){}
    public static void open(Activity a){
        if(RootPanel.blocked(a))return;
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiDesign.dp(a,16),UiDesign.dp(a,12),UiDesign.dp(a,16),UiDesign.dp(a,8));
        TextView cache=UiDesign.text(a,UiDesign.words("正在读取触点缓存…","Reading saved coordinates…"),15,UiDesign.ACCENT,true);
        cache.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(cache);
        String help=UiDesign.words("导入或下载时计算落点。更改触控参数或屏幕范围后，请在演奏前重新准备谱面。",
            "Importing or downloading prepares the touch coordinates. Prepare them again after changing the touch settings or play area.");
        body.addView(UiDesign.text(a,help,14,UiDesign.MUTED,false),UiDesign.space(a,-1,-2,8));
        ScrollView scroll=new ScrollView(a);scroll.addView(body);
        AlertDialog dialog=UiDesign.dialog(a,tr("预计算触点")).setView(scroll).setNegativeButton(UiDesign.words("关闭","Close"),null).create();
        body.addView(UiDesign.row(a,UiDesign.LIBRARY,UiDesign.words("准备 Phigros 谱面","Prepare a Phigros chart"),
            UiDesign.words("选择歌曲和难度","Choose a song and difficulty"),()->{dialog.dismiss();RootPanel.choosePreparation(a);}),UiDesign.space(a,-1,-2,12));
        body.addView(UiDesign.row(a,UiDesign.IMPORT,UiDesign.words("准备已导入谱面","Prepare imported charts"),
            UiDesign.words("批量准备本地谱面和已缓存的下载","Prepare local charts and cached downloads"),()->{dialog.dismiss();start(a,null,null,null);}),UiDesign.space(a,-1,-2,4));
        UiDesign.show(dialog);
        new Thread(()->{
            String value;
            try{
                long[] main=PreparedChartStore.statistics(a),local=PreparedChartStore.statistics(ExternalCharts.localStorage(a));
                String format=UiDesign.words("已缓存 %d 份轨迹 · %.1f MiB","Saved trajectories: %d · %.1f MiB");
                value=String.format(Locale.getDefault(),format,main[0]+local[0],(main[1]+local[1])/1048576.0);
            }catch(Exception e){value=tr("无法读取触点缓存");}
            final String result=value;
            a.runOnUiThread(()->{if(RootPanel.alive(a)&&dialog.isShowing())UiMotion.state(cache,result);});
        },"phi-prepare-statistics").start();
    }
    static void start(Activity a,ChartLibrary selectedLibrary,ChartLocation selected,String label){
        if(RootPanel.blocked(a)||!busy.compareAndSet(false,true))return;AtomicBoolean stop=new AtomicBoolean();
        AlertDialog dialog=UiDesign.dialog(a,tr("预计算触点")).setCancelable(false)
            .setMessage(label==null?UiDesign.words("正在读取本地谱库…","Reading local libraries…"):label)
            .setNegativeButton(tr("取消"),null).create();
        UiDesign.show(dialog);
        Button cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        cancel.setOnClickListener(v->{
            stop.set(true);PhiraImportControl.cancel();cancel.setEnabled(false);
            dialog.setMessage(UiDesign.words("正在取消，已保存的谱面会保留。","Cancelling. Your saved charts will be kept."));
        });
        new Thread(()->{
            String result;
            try(PhiraImportControl.Session session=PhiraImportControl.begin()){
                if(stop.get()||RuntimeState.INSTANCE.getRunning().get())throw new java.util.concurrent.CancellationException(tr("已取消"));
                int count=0;
                if(selected!=null){
                    ExternalCharts.importSelected(a,selectedLibrary,selected.getSongId(),selected.getDifficulty(),stop);
                    count=1;
                }else{
                    Context local=ExternalCharts.localStorage(a);
                    long nextUpdate=0;
                    for(Context context:new Context[]{a,local}){
                        PhiraLibrary library=PhiraLibrary.Companion.restore(context);
                        if(library!=null)for(PhiraEntry entry:library.getEntries()){
                            if(stop.get())throw new java.util.concurrent.CancellationException(tr("已取消"));
                            long now=SystemClock.uptimeMillis();
                            if(now>=nextUpdate){
                                String status=String.format(Locale.getDefault(),UiDesign.words("已完成 %d 张","Charts prepared: %d"),count)
                                    +"\n"+entry.getTitle()+" / "+entry.getLevel();
                                progress(a,dialog,stop,status);nextUpdate=now+200;
                            }
                            PreparedChartStore.preparePhira(context,library,entry,stop);
                            count++;
                        }
                    }
                    progress(a,dialog,stop,UiDesign.words("正在准备已缓存的下载…","Preparing cached downloads…"));
                    count+=ExternalCharts.prepareCached(a,stop);
                }
                result=String.format(java.util.Locale.ROOT,UiDesign.words("已完成 %d 张谱面的触点预计算。","Touch coordinates prepared for %d charts."),count);
            }catch(Exception e){result=stop.get()||Thread.currentThread().isInterrupted()?tr("已取消；已保存的谱面会保留。"):tr("预计算未完成：")+e.getMessage();}
            finally{busy.set(false);}
            final String message=result;a.runOnUiThread(()->{if(RootPanel.alive(a)){dialog.dismiss();HomeScreen.refresh(a);RootPanel.message(a,tr("预计算触点"),message);}});
        },"phi-prepare-charts").start();
        HomeScreen.refresh(a);
    }
    private static void progress(Activity a,AlertDialog dialog,AtomicBoolean stop,String message){
        a.runOnUiThread(()->{
            if(stop.get()||!RootPanel.alive(a)||!dialog.isShowing())return;
            TextView text=dialog.findViewById(android.R.id.message);
            if(text!=null)UiMotion.state(text,message);else dialog.setMessage(message);
        });
    }
}
