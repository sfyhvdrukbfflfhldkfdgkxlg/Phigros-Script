package android.media;
import java.nio.ByteBuffer;
public class Image {
    final long time;final Plane plane;public Image(long t,int pixel){time=t;plane=new Plane(pixel);}
    public long getTimestamp(){return time;}public int getWidth(){return 2;}public int getHeight(){return 2;}
    public Plane[] getPlanes(){return new Plane[]{plane};}
    public static class Plane{ByteBuffer bytes=ByteBuffer.allocate(20);Plane(int p){bytes.put(0,(byte)p);}
        public ByteBuffer getBuffer(){return bytes;}public int getPixelStride(){return 4;}public int getRowStride(){return 12;}}
}
