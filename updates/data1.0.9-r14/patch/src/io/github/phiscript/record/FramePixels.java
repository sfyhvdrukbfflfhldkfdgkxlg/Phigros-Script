package io.github.phiscript.record;

import android.graphics.Bitmap;
import android.media.Image;
import java.nio.ByteBuffer;

/** A reusable RGBA frame. ImageReader owns the input Image and closes it after the callback. */
final class FramePixels {
    Bitmap bitmap;
    private ByteBuffer packed;
    void copy(Image image){
        final int w=image.getWidth(),h=image.getHeight();
        if(bitmap==null||bitmap.getWidth()!=w||bitmap.getHeight()!=h){
            release();bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        }
        Image.Plane plane=image.getPlanes()[0];
        if(plane.getPixelStride()!=4)throw new IllegalArgumentException("投屏图像并非 RGBA 像素");
        ByteBuffer data=plane.getBuffer().duplicate();
        if(plane.getRowStride()==w*4){bitmap.copyPixelsFromBuffer(data);return;}
        if(packed==null)packed=ByteBuffer.allocateDirect(w*h*4);
        copyRows(data,plane.getRowStride(),w,h,packed);bitmap.copyPixelsFromBuffer(packed);
    }
    static void copyRows(ByteBuffer src,int stride,int width,int height,ByteBuffer dst){
        int base=src.position(),limit=src.limit(),row=width*4;
        if(row<=0||height<=0||stride<row||(long)base+(long)(height-1)*stride+row>limit)throw new IllegalArgumentException("无效的图像行跨度");
        dst.clear();
        for(int y=0;y<height;y++){
            int start=base+y*stride;src.limit(limit);src.position(start);src.limit(start+row);dst.put(src);
        }
        dst.flip();
    }
    void release(){if(bitmap!=null)bitmap.recycle();bitmap=null;packed=null;}
}
