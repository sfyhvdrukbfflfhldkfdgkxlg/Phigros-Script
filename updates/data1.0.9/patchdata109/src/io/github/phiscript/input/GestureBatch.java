package io.github.phiscript.input;

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
            throw new IllegalArgumentException("触点时间无效，已停止");
        Item<K> item = new Item<>();
        item.key=key; item.prior=prior; item.path=path; item.down=down; item.up=up;
        item.keep=keep; item.x=x; item.y=y;
        items.add(item);
        if (prior == null) starts++; else continuing++;
    }

    GestureDescription build() {
        return build(null);
    }
    GestureDescription build(StrokeIdentityPool pool) {
        if (items.isEmpty() || items.size() > GestureChannel.POINTER_LIMIT)
            throw new IllegalStateException("触点批次超过系统安全容量，已停止");
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
            if (++passes > 40) throw new IllegalStateException("无法安全排列触点，已停止");
        } while (changed);
        // Preserve the order of continuing fingers. New fingers enter in the
        // same order as their DOWN times, matching the framework's pointer list.
        Collections.sort(items, new Comparator<Item<K>>() {
            @Override public int compare(Item<K> a, Item<K> b) {
                if (a.prior != null && b.prior == null) return -1;
                if (a.prior == null && b.prior != null) return 1;
                return a.prior != null ? 0 : Long.compare(a.down, b.down);
            }
        });
        GestureDescription.Builder builder = new GestureDescription.Builder();
        next.clear(); duration=1;
        for (Item<K> item : items) {
            if (continuing > 0 && item.prior == null && item.down < 2)
                throw new IllegalStateException("新触点不能占用续接握手，已停止");
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
}
