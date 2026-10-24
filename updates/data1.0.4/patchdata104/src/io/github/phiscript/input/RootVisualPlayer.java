package io.github.phiscript.input;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import io.github.phiscript.root.RootClient;
import io.github.phiscript.root.RootTouchState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public final class RootVisualPlayer {
    private static final class Shot{
        final VisualTouch touch;final long born,key;long start=-1;
        Shot(VisualTouch t,long born,long key){touch=t;this.born=born;this.key=key;}
    }
    public static void play(AccessibilityService service,int width,int height,Function0<VisualTouchFrame> snapshot,
            AtomicBoolean stop,Function0<Boolean> allowed,Function1<? super String,Unit> status)throws InterruptedException{
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("触控必须在后台线程执行");
        VisualChordState state=new VisualChordState(GestureProfile.read(service).catchDrags);Map<Long,Long> ids=new LinkedHashMap<>();
        TouchSafety safety=TouchSafety.read(service,width,height,null);TouchBounds bounds=safety.bounds;
        ArrayDeque<Shot> pending=new ArrayDeque<>();List<Shot> shots=new ArrayList<>();long serial=1;
        try(RootClient client=new RootClient(service,width,height)){
            long began=SystemClock.uptimeMillis();boolean seenFrame=false;
            status.invoke("Root 视觉多点演奏已开始；音量减停止");
            while(!stop.get()&&Boolean.TRUE.equals(allowed.invoke())){
                VisualTouchFrame frame=snapshot.invoke();long now=SystemClock.uptimeMillis();
                if(frame==null){
                    if(!seenFrame&&now-began<2000){client.frame(new ArrayList<RootTouchState.Sample>());GestureChannel.sleep(6);continue;}
                    throw new IllegalStateException("未收到视觉画面，已释放触点");
                }
                seenFrame=true;
                if(frame.getScreenWidth()!=width||frame.getScreenHeight()!=height)throw new IllegalStateException("屏幕尺寸变化");
                VisualTouchDemand demand=state.update(frame,now);
                Map<Long,VisualTouch> holds=new LinkedHashMap<>();for(VisualTouch t:demand.getHolds())holds.put(t.getId(),t);
                ids.keySet().retainAll(holds.keySet());
                for(Long id:holds.keySet())if(!ids.containsKey(id))ids.put(id,serial++);
                for(VisualTouch t:demand.getShots())pending.add(new Shot(t,now,serial++));
                if(pending.size()>128)throw new IllegalStateException("视觉点击积压过多");
                Iterator<Shot> ended=shots.iterator();while(ended.hasNext()){
                    Shot s=ended.next();if(now-s.start>=(s.touch.getKind()==VisualTouchKind.FLICK?30:18))ended.remove();
                }
                if(holds.size()+shots.size()>10)throw new IllegalStateException("视觉触点超过十指，已安全停止");
                while(!pending.isEmpty()&&now-pending.peek().born>200)pending.remove();
                while(!pending.isEmpty()&&holds.size()+shots.size()<10){Shot s=pending.remove();s.start=now;shots.add(s);}
                List<RootTouchState.Sample> samples=new ArrayList<>();
                for(VisualTouch t:holds.values())samples.add(new RootTouchState.Sample(ids.get(t.getId()),bounds.x(t.getX()*(width-1)),bounds.y(t.getY()*(height-1))));
                for(Shot s:shots){VisualTouch t=s.touch;float x=bounds.x(t.getX()*(width-1)),y=bounds.y(t.getY()*(height-1));
                    if(t.getKind()==VisualTouchKind.FLICK){float tx,ty;
                        if(safety.inwardFlick||safety.enabled){float[] end=bounds.flick(x,y,t.getFlickDx()*width,t.getFlickDy()*height);tx=end[0];ty=end[1];}
                        else{FlickGeometry.Pixel end=FlickGeometry.INSTANCE.endpoint(x,y,t.getFlickDx()*width,t.getFlickDy()*height,width,height);tx=end.getX();ty=end.getY();}
                        float progress=Math.min(1,(now-s.start)/30f);x+=(tx-x)*progress;y+=(ty-y)*progress;}
                    samples.add(new RootTouchState.Sample(s.key,x,y));
                }
                client.frame(samples);GestureChannel.sleep(6);
            }
            client.frame(new ArrayList<RootTouchState.Sample>());status.invoke("已停止 Root 视觉触摸");
        }catch(InterruptedException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Root 视觉演奏失败："+e.getMessage(),e);}
    }
}
