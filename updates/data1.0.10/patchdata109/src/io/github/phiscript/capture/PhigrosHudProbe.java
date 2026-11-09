package io.github.phiscript.capture;

import android.graphics.Bitmap;
import android.graphics.RectF;
import io.github.phiscript.vision.NormalizedPoint;
import io.github.phiscript.vision.PauseGlyphDetector;

/** Reuses the existing Phigros pause-glyph detector without delaying the clock by its vote gate. */
final class PhigrosHudProbe {
    private final RectF roi;
    private int[] pixels=new int[0];
    PhigrosHudProbe(RectF roi){
        if(roi.left<0||roi.top<0||roi.right>1||roi.bottom>1||roi.width()<=0||roi.height()<=0)throw new IllegalArgumentException("暂停检测区域无效");
        this.roi=new RectF(roi);
    }
    NormalizedPoint observe(Bitmap bitmap){
        int width=bitmap.getWidth(),height=bitmap.getHeight();if(width<=height||bitmap.isRecycled())return null;
        int left=Math.max(0,(int)(roi.left*width)),top=Math.max(0,(int)(roi.top*height));
        int w=Math.min(width-left,Math.max(1,(int)(roi.width()*width))),h=Math.min(height-top,Math.max(1,(int)(roi.height()*height)));
        if(pixels.length!=w*h)pixels=new int[w*h];
        bitmap.getPixels(pixels,0,w,left,top,w,h);
        PauseGlyphDetector.Glyph glyph=PauseGlyphDetector.INSTANCE.detect(pixels,w,h,Math.min(width,height));
        if(glyph==null)return null;
        return new NormalizedPoint((left+glyph.getCenterX())/width,(top+glyph.getCenterY())/height);
    }
}
