package android.graphics;
import java.nio.Buffer;import java.nio.ByteBuffer;
public class Bitmap {
    public enum Config{ARGB_8888}
    private int width,height;public int pixel;public boolean recycled;public static int allocations,live;
    public static Bitmap createBitmap(int w,int h,Config c){Bitmap b=new Bitmap();b.width=w;b.height=h;allocations++;live++;return b;}
    public int getWidth(){return width;}public int getHeight(){return height;}
    public void copyPixelsFromBuffer(Buffer buf){if(recycled)throw new AssertionError("use after recycle");pixel=((ByteBuffer)buf).get()&255;}
    public void recycle(){if(recycled)throw new AssertionError("double free");recycled=true;live--;}
}
