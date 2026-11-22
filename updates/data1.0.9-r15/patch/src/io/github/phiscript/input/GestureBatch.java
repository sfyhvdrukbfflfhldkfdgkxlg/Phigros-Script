package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps framework pointer indices valid when an UP and a DOWN coincide. */
final class GestureBatch<K> {
    private static final class Item<K> {
        K key; GestureChannel.Live prior; Path path;
        long down, up; boolean keep; float x, y;
    }
    private final List<Item<K>> items = new ArrayList<>();
    final Map<K, GestureChannel.Live> next = new LinkedHashMap<>();
    int continuing, starts;
    long duration;

    void add(K key, GestureChannel.Live prior, Path path, long down, long up,
            boolean keep, float x, float y) {
        if (down < 0 || up <= down || (prior != null && down != 0))
            throw new IllegalArgumentException(tr("触点时间无效，已停止"));
        Item<K> item = new Item<>();
        item.key=key; item.prior=prior; item.path=path; item.down=down; item.up=up;
        item.keep=keep; item.x=x; item.y=y;
        items.add(item);
        if (prior == null) starts++; else continuing++;
    }

    boolean isEmpty() { return items.isEmpty(); }
    long startOffset(K key) {
        for(Item<K> item:items)if(item.key==key || item.key.equals(key))return item.prior==null?item.down:-1;
        return -1;
    }
    interface Safety<K> {boolean clear(K key,GestureChannel.Live prior,long down,long up,boolean keep);}
    List<K> rejectUnsafe(Safety<K> safety){
        normalize();List<K> rejected=new ArrayList<>();
        for(java.util.Iterator<Item<K>> it=items.iterator();it.hasNext();){
            Item<K> item=it.next();if(safety.clear(item.key,item.prior,item.down,item.up,item.keep))continue;
            rejected.add(item.key);
            if(item.prior==null){it.remove();starts--;}
            else{item.path=GestureChannel.path(item.prior.x,item.prior.y,item.prior.x,item.prior.y);item.down=0;item.up=1;item.keep=false;item.x=item.prior.x;item.y=item.prior.y;}
        }
        return rejected;
    }
    long normalizedEndOffset(){normalize();return endOffset();}
    long endOffset(){long end=0;for(Item<K> item:items)end=Math.max(end,item.up);return end;}
    void rebase(long elapsed) {
        if(elapsed<=0)return;
        for(Item<K> item:items){
            long minimum=item.prior==null && continuing>0 ? 2 : 0;
            item.down=Math.max(minimum,item.down-elapsed);
            item.up=Math.max(item.down+1,item.up-elapsed);
        }
    }

    GestureDescription build() {
        return build(null);
    }
    GestureDescription build(StrokeIdentityPool pool) {
        normalize();
        GestureDescription.Builder builder = new GestureDescription.Builder();
        next.clear(); duration=1;
        for (Item<K> item : items) {
            if (continuing > 0 && item.prior == null && item.down < 2)
                throw new IllegalStateException(tr("新触点不能占用续接握手，已停止"));
            GestureDescription.StrokeDescription stroke = item.prior == null
                ? new GestureDescription.StrokeDescription(item.path, item.down,
                    item.up-item.down, item.keep)
                : item.prior.stroke.continueStroke(item.path, 0,
                    item.up-item.down, item.keep);
            if(pool!=null)pool.apply(item.key,stroke,item.prior==null?null:item.prior.stroke);
            builder.addStroke(stroke);
            if (item.keep) next.put(item.key, new GestureChannel.Live(stroke,item.x,item.y));
            duration=Math.max(duration,item.up);
        }
        return builder.build();
    }
    private void normalize(){
        if (items.isEmpty() || items.size() > GestureChannel.POINTER_LIMIT)
            throw new IllegalStateException(tr("触点批次超过系统安全容量，已停止"));
        // MotionEventInjector removes UPs before adding DOWNs, but uses the
        // original step index for a new pointer. Leave at least 1 ms between
        // those operations so ended points are absent from the DOWN step.
        boolean changed;
        int passes=0;
        do {
            changed=false;
            for (Item<K> ending : items) if (!ending.keep) {
                for (Item<K> starting : items) if (starting.prior == null
                        && starting.down == ending.up) {
                    if (ending.up - ending.down > 1) ending.up--;
                    else {
                        starting.down++;
                        starting.up=Math.max(starting.up, starting.down+1);
                    }
                    changed=true;
                }
            }
            if (++passes > 40) throw new IllegalStateException(tr("无法安全排列触点，已停止"));
        } while (changed);
        // MotionEventInjector indexes a DOWN relative to every still-held pointer.
        // A stroke marked for continuation must appear in every later sampled step
        // in this batch, including a fresh DOWN delayed by the handshake.
        long lastStep=0;
        for(Item<K> item:items)lastStep=Math.max(lastStep,item.up);
        for(Item<K> item:items)if(item.keep)item.up=lastStep;
        // Preserve the order of continuing fingers. New fingers enter in the
        // same order as their DOWN times, matching the framework's pointer list.
        Collections.sort(items, new Comparator<Item<K>>() {
            @Override public int compare(Item<K> a, Item<K> b) {
                if (a.prior != null && b.prior == null) return -1;
                if (a.prior == null && b.prior != null) return 1;
                return a.prior != null ? 0 : Long.compare(a.down, b.down);
            }
        });
    }
}
