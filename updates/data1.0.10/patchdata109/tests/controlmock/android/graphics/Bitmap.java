package android.graphics;
import java.util.Arrays;
public final class Bitmap {
 private final int width,height;private final int[] pixels;private boolean recycled;
 public Bitmap(int w,int h){width=w;height=h;pixels=new int[w*h];Arrays.fill(pixels,0xff202020);}
 public int getWidth(){return width;}public int getHeight(){return height;}public boolean isRecycled(){return recycled;}public void recycle(){recycled=true;}
 public int getPixel(int x,int y){if(x<0||y<0||x>=width||y>=height)throw new IllegalArgumentException();return pixels[y*width+x];}
 public void setPixel(int x,int y,int color){if(x>=0&&y>=0&&x<width&&y<height)pixels[y*width+x]=color;}
 public void getPixels(int[] out,int offset,int stride,int x,int y,int w,int h){for(int row=0;row<h;row++)System.arraycopy(pixels,(y+row)*width+x,out,offset+row*stride,w);}
}
