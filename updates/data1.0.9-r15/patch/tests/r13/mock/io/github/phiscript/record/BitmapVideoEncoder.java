package io.github.phiscript.record;
import android.content.Context;import android.graphics.Bitmap;import java.util.*;
final class BitmapVideoEncoder {
    static List<Long> pts=new ArrayList<>();static List<Integer> pixels=new ArrayList<>();static boolean saved,fail;
    static RecordingPolicy.Profile selected;static int[] size;
    BitmapVideoEncoder(Context c,int w,int h){this(c,w,h,RecordingPolicy.DEFAULT);}
    BitmapVideoEncoder(Context c,int w,int h,RecordingPolicy.Profile profile){selected=profile;size=RecordingPolicy.size(w,h,profile);}
    void frame(Bitmap b,long ns){if(fail)throw new IllegalStateException("injected encoder failure");if(b.recycled)throw new AssertionError();pts.add(ns);pixels.add(b.pixel);}
    void close(boolean success){saved=success;}
}
