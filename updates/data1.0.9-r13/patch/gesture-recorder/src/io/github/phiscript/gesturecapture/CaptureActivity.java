package io.github.phiscript.gesturecapture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

public final class CaptureActivity extends Activity {
    private static final int EXPORT=21;
    private static final ExecutorService FILES=Executors.newSingleThreadExecutor();
    private EditText name;
    private TextView status;
    private Button record,export;
    private Pad pad;
    private GestureTrace trace;
    private JSONObject saved;
    private AtomicFile draft;
    private File snapshot;
    private int revision;
    private void note(String message){status.setText(message);}
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);draft=new AtomicFile(new File(getFilesDir(),"last-trace.json"));snapshot=new File(getFilesDir(),"export.json");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));setContentView(root);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(android.view.Gravity.CENTER_VERTICAL);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.addView(controls);root.addView(scroll);
        name=new EditText(this);name.setHint("手势名称");name.setSingleLine(true);name.setMinWidth(dp(105));controls.addView(name);
        record=button(controls,"开始录入",()->{if(trace==null)begin();else finishTrace("手动停止");});
        export=button(controls,"导出 JSON",this::export);export.setEnabled(false);
        button(controls,"清空",()->{if(trace!=null)return;new AlertDialog.Builder(this).setMessage("清空当前手势？").setNegativeButton("取消",null)
            .setPositiveButton("清空",(d,w)->{revision++;saved=null;export.setEnabled(false);FILES.execute(()->draft.delete());pad.clear();note("手势已清空");}).show();});
        status=new TextView(this);root.addView(status);note("开始录入后可连续做多次单指或多指手势。");
        pad=new Pad(this);root.addView(pad,new LinearLayout.LayoutParams(-1,0,1));
        final int ticket=revision;
        FILES.execute(()->{JSONObject previous=null;try{previous=new JSONObject(new String(draft.readFully(),StandardCharsets.UTF_8));}catch(Exception ignored){}
            final JSONObject result=previous;runOnUiThread(()->{if(isDestroyed()||ticket!=revision||result==null)return;saved=result;name.setText(result.optString("name"));export.setEnabled(true);note("已恢复上次手势，可重新录入或导出。");});});
    }
    private Button button(LinearLayout parent,String label,Runnable fn){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->fn.run());parent.addView(b);return b;}
    private void begin(){
        String label=name.getText().toString().trim();if(label.isEmpty())label="手势";
        try{trace=new GestureTrace(label,pad.getWidth(),pad.getHeight(),SystemClock.uptimeMillis());}
        catch(RuntimeException e){note(e.getMessage());return;}
        revision++;pad.clear();pad.setKeepScreenOn(true);name.setEnabled(false);record.setText("停止录入");export.setEnabled(false);note("正在录入，抬起后可继续录入下一段。");
    }
    private void finishTrace(String reason){
        GestureTrace pending=trace;if(pending==null)return;
        trace=null;pad.setKeepScreenOn(false);name.setEnabled(true);record.setText("开始录入");
        try{
            if(pending.hasSamples()){
                JSONObject result=pending.finish(SystemClock.uptimeMillis(),reason);saved=result;
                int ticket=++revision;
                FILES.execute(()->{FileOutputStream out=null;try{out=draft.startWrite();out.write(result.toString().getBytes(StandardCharsets.UTF_8));draft.finishWrite(out);}
                    catch(Exception e){if(out!=null)draft.failWrite(out);runOnUiThread(()->{if(ticket==revision&&!isDestroyed())note("草稿保存失败，可直接导出。");});}});
                note(reason+"，共 "+result.getJSONArray("events").length()+" 帧。");
            }else note("本次没有录入触点。");
        }catch(JSONException e){note("录入数据无效");}
        export.setEnabled(saved!=null);
    }
    private void export(){
        if(saved==null||trace!=null)return;
        final JSONObject data=saved;export.setEnabled(false);
        FILES.execute(()->{try(FileOutputStream out=new FileOutputStream(snapshot)){out.write(data.toString(2).getBytes(StandardCharsets.UTF_8));}
            catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){note("准备导出失败");export.setEnabled(saved!=null&&trace==null);}});return;}
            runOnUiThread(()->{if(isDestroyed())return;export.setEnabled(saved!=null&&trace==null);
                Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"gesture.json");
                try{startActivityForResult(picker,EXPORT);}catch(RuntimeException e){note("找不到系统文件选择器");}});});
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);if(request!=EXPORT||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        final android.net.Uri uri=intent.getData();FILES.execute(()->{
            try(InputStream in=new FileInputStream(snapshot);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException();byte[] buf=new byte[16384];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);out.flush();
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())note("导出失败，可重新选择文件");});return;}
            runOnUiThread(()->{if(!isDestroyed())note("JSON 已导出");});});
    }
    @Override protected void onPause(){finishTrace("离开录入页面");super.onPause();}
    private final class Pad extends View {
        private final int[] inks={0xff5bdbc4,0xffffb963,0xff94b8ff,0xffff9bba};
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<Integer,Path> paths=new HashMap<>();
        Pad(Context c){super(c);setContentDescription("手势录入画布");setBackgroundColor(0xff18252c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(3));}
        void clear(){paths.clear();invalidate();}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(trace!=null&&oldw>0)finishTrace("画布尺寸改变");}
        private void sample(MotionEvent e,String action,int id,int history)throws JSONException{
            int count=e.getPointerCount();int[] ids=new int[count];float[] x=new float[count],y=new float[count];
            for(int i=0;i<count;i++){ids[i]=e.getPointerId(i);x[i]=history<0?e.getX(i):e.getHistoricalX(i,history);y[i]=history<0?e.getY(i):e.getHistoricalY(i,history);}
            trace.frame(action,id,history<0?e.getEventTime():e.getHistoricalEventTime(history),ids,x,y,history>=0);
            for(int i=0;i<count;i++){
                Path path=paths.get(ids[i]);if(path==null){path=new Path();paths.put(ids[i],path);}
                if(action.equals("down")&&ids[i]==id){path.moveTo(x[i],y[i]);path.lineTo(x[i]+.02f,y[i]+.02f);}
                else path.lineTo(x[i],y[i]);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(trace==null)return true;
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_CANCEL){finishTrace("系统取消触摸");return true;}
            if(!trace.hasContacts()&&a!=MotionEvent.ACTION_DOWN)return true;
            try{
                if(a==MotionEvent.ACTION_MOVE){for(int h=0;h<e.getHistorySize();h++)sample(e,"move",-1,h);sample(e,"move",-1,-1);}
                else if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){getParent().requestDisallowInterceptTouchEvent(true);sample(e,"down",e.getPointerId(e.getActionIndex()),-1);}
                else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP){sample(e,"up",e.getPointerId(e.getActionIndex()),-1);if(a==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();}}
                invalidate();
            }catch(RuntimeException|JSONException error){finishTrace(error.getMessage()==null?"采样中断":error.getMessage());}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);for(Map.Entry<Integer,Path> e:paths.entrySet()){paint.setColor(inks[e.getKey()%inks.length]);c.drawPath(e.getValue(),paint);}}
    }
}
