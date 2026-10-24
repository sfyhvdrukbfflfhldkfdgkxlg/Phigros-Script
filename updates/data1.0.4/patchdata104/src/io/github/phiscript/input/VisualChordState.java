package io.github.phiscript.input;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keep each hold by tracking ID; a missed image does not release every hold. */
public final class VisualChordState {
    public static final long HOLD_GRACE_MS = 90;
    private long latest = Long.MIN_VALUE;
    private int width, height;
    private final Map<Long, VisualTouch> holds = new LinkedHashMap<>();
    private final Map<Long, Long> seen = new LinkedHashMap<>();
    private final Map<Long, Long> consumed = new LinkedHashMap<>();
    private final boolean catchDrags;
    private final Map<Long,Long> dragAliases=new LinkedHashMap<>();
    public VisualChordState(){this(false);}
    public VisualChordState(boolean catchDrags){this.catchDrags=catchDrags;}

    public VisualTouchDemand update(VisualTouchFrame frame, long now) {
        if (frame == null || frame.getCapturedAtMs() > now
                || now - frame.getCapturedAtMs() > 350)
            throw new IllegalStateException("视觉画面已过期，已停止并释放触点");
        if (frame.getCapturedAtMs() < latest)
            throw new IllegalStateException("视觉画面时间倒退");
        if (width == 0) { width = frame.getScreenWidth(); height = frame.getScreenHeight(); }
        if (width <= 1 || height <= 1 || width != frame.getScreenWidth()
                || height != frame.getScreenHeight())
            throw new IllegalStateException("屏幕尺寸变化，请重新开始");
        List<VisualTouch> shots = new ArrayList<>();
        Set<Long> present = new HashSet<>();
        Set<Long> currentHolds = new HashSet<>();
        if (frame.getTouches().size() > 256)
            throw new IllegalArgumentException("单帧识别结果过多");
        // Existing long presses can catch a yellow note without another DOWN.
        if(catchDrags)for(VisualTouch t:frame.getTouches())if(t.getKind()==VisualTouchKind.HOLD){holds.put(t.getId(),t);seen.put(t.getId(),frame.getCapturedAtMs());currentHolds.add(t.getId());}
        for (VisualTouch t : frame.getTouches()) {
            if (!present.add(t.getId())) throw new IllegalArgumentException("视觉触点 ID 重复");
            valid(t.getX()); valid(t.getY());
            if (t.getX() < 0 || t.getX() > 1 || t.getY() < 0 || t.getY() > 1)
                throw new IllegalArgumentException("视觉触点坐标超出屏幕");
            if(catchDrags&&t.getKind()==VisualTouchKind.DRAG){
                boolean covered=false;
                for(Long id:currentHolds){VisualTouch h=holds.get(id);if(h!=null&&h.getKind()==VisualTouchKind.HOLD&&distance(t,h)<1){covered=true;break;}}
                if(covered)continue;
                Long canonical=dragAliases.get(t.getId());
                if(canonical==null||!holds.containsKey(canonical)){
                    canonical=null;
                    double best=Double.POSITIVE_INFINITY;
                    for(Map.Entry<Long,VisualTouch> entry:holds.entrySet())if(entry.getValue().getKind()==VisualTouchKind.DRAG&&!currentHolds.contains(entry.getKey())&&frame.getCapturedAtMs()-seen.get(entry.getKey())<=1200){
                        double d=distance(t,entry.getValue());if(d<=height*.12&&d<best){best=d;canonical=entry.getKey();}
                    }
                    if(canonical==null)canonical=t.getId();dragAliases.put(t.getId(),canonical);
                }
                t=new VisualTouch(canonical,VisualTouchKind.DRAG,t.getX(),t.getY(),t.getFlickDx(),t.getFlickDy());
            }
            if (t.getKind() == VisualTouchKind.HOLD || t.getKind() == VisualTouchKind.DRAG) {
                currentHolds.add(t.getId());
                holds.put(t.getId(), t);
                seen.put(t.getId(), frame.getCapturedAtMs());
            } else {
                valid(t.getFlickDx()); valid(t.getFlickDy());
                if (!consumed.containsKey(t.getId())) {
                    shots.add(t);
                    consumed.put(t.getId(), now);
                }
                // A tracker can reclassify a blob. End its old hold before
                // sending a shot instead of crashing with an ID conflict.
                holds.remove(t.getId()); seen.remove(t.getId());
            }
        }
        latest = frame.getCapturedAtMs();
        Iterator<Map.Entry<Long, VisualTouch>> it = holds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, VisualTouch> e = it.next();
            if (!currentHolds.contains(e.getKey())
                    && now - seen.get(e.getKey()) > (catchDrags&&e.getValue().getKind()==VisualTouchKind.DRAG?1200:HOLD_GRACE_MS)) {
                seen.remove(e.getKey()); it.remove();
            }
        }
        // Grace contacts yield to newly recognized holds at a chord handoff.
        if (holds.size() > GestureChannel.POINTER_LIMIT) {
            it = holds.entrySet().iterator();
            while (it.hasNext() && holds.size() > GestureChannel.POINTER_LIMIT) {
                Map.Entry<Long, VisualTouch> e = it.next();
                if (!currentHolds.contains(e.getKey())) {
                    seen.remove(e.getKey()); it.remove();
                }
            }
        }
        if (holds.size() > GestureChannel.POINTER_LIMIT)
            throw new IllegalStateException("视觉长键超过 10 个独立触点");
        Iterator<Map.Entry<Long, Long>> old = consumed.entrySet().iterator();
        while (old.hasNext()) if (now - old.next().getValue() > 10000) old.remove();
        dragAliases.values().removeIf(id->!holds.containsKey(id));
        return new VisualTouchDemand(new ArrayList<>(holds.values()), shots);
    }

    private double distance(VisualTouch a,VisualTouch b){return Math.hypot((a.getX()-b.getX())*width,(a.getY()-b.getY())*height);}

    private static void valid(float f) {
        if (Float.isInfinite(f) || Float.isNaN(f))
            throw new IllegalArgumentException("视觉触点坐标无效");
    }
}
