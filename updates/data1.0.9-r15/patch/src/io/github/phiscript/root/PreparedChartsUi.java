package io.github.phiscript.root;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.ExternalCharts;
import io.github.phiscript.assets.PhiraEntry;
import io.github.phiscript.assets.PhiraLibrary;
import io.github.phiscript.input.PreparedChartStore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PreparedChartsUi {
    private static final AtomicBoolean busy=new AtomicBoolean();
    public static boolean isBusy(){return busy.get();}
    private PreparedChartsUi(){}
    public static void open(Activity a){
        if(RootPanel.blocked(a))return;
        try{
            long[] main=PreparedChartStore.statistics(a),local=PreparedChartStore.statistics(ExternalCharts.localStorage(a));
            String detail="已缓存 "+(main[0]+local[0])+" 份轨迹，约 "+String.format(java.util.Locale.ROOT,"%.1f",(main[1]+local[1])/1048576.0)+" MiB。\n\n导入或下载时提前计算；修改触控参数或屏幕范围后，会在演奏开始前重算。手动重算也会刷新随机偏移。计算期间可取消，原始谱面会保留。";
            UiDesign.show(UiDesign.dialog(a,"预计算触点").setMessage(detail).setPositiveButton("重新计算",(d,w)->start(a)).setNegativeButton("关闭",null).create());
        }catch(Exception e){RootPanel.message(a,"无法读取触点缓存",String.valueOf(e.getMessage()));}
    }
    private static void start(Activity a){
        if(RootPanel.blocked(a)||!busy.compareAndSet(false,true))return;AtomicBoolean stop=new AtomicBoolean();
        ProgressDialog dialog=new ProgressDialog(a);dialog.setTitle("预计算触点");dialog.setMessage("正在计算，请稍候。可在运行记录查看进度。");dialog.setIndeterminate(true);dialog.setCancelable(false);
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",(d,w)->{stop.set(true);PhiraImportControl.cancel();});UiDesign.show(dialog);
        new Thread(()->{
            String result;
            try(PhiraImportControl.Session session=PhiraImportControl.begin()){
                if(stop.get()||RuntimeState.INSTANCE.getRunning().get())throw new java.util.concurrent.CancellationException("已取消");
                PreparedChartStore.clear(a);PreparedChartStore.clear(ExternalCharts.localStorage(a));
                int count=0;PhiraLibrary library=PhiraLibrary.Companion.restore(a);
                if(library!=null)for(PhiraEntry entry:library.getEntries()){if(stop.get())throw new java.util.concurrent.CancellationException("已取消");library.load(entry.getId(),entry.getLevel());count++;}
                library=PhiraLibrary.Companion.restore(ExternalCharts.localStorage(a));
                if(library!=null)for(PhiraEntry entry:library.getEntries()){if(stop.get())throw new java.util.concurrent.CancellationException("已取消");library.load(entry.getId(),entry.getLevel());count++;}
                count+=ExternalCharts.prepareCached(a,stop);result="已完成 "+count+" 张谱面的触点预计算。";
            }catch(Exception e){result=stop.get()||Thread.currentThread().isInterrupted()?"已取消；已保存的谱面会保留。":"预计算未完成："+e.getMessage();}
            finally{busy.set(false);}
            final String message=result;a.runOnUiThread(()->{if(RootPanel.alive(a)){dialog.dismiss();HomeScreen.refresh(a);RootPanel.message(a,"预计算触点",message);}});
        },"phi-prepare-charts").start();
        HomeScreen.refresh(a);
    }
}
