package android.graphics;
public final class Bitmap {
 public final int width,height;public final int[] pixels;private boolean recycled;
 public Bitmap(int width,int height){this(width,height,new int[width*height]);}
 public Bitmap(int width,int height,int[] pixels){this.width=width;this.height=height;this.pixels=pixels;}
 public int getWidth(){return width;}public int getHeight(){return height;}public boolean isRecycled(){return recycled;}public void recycle(){recycled=true;}
 public int getPixel(int x,int y){return pixels[y*width+x];}public void setPixel(int x,int y,int color){pixels[y*width+x]=color;}
 public void getPixels(int[] target,int offset,int stride,int x,int y,int w,int h){for(int row=0;row<h;row++)System.arraycopy(pixels,(y+row)*width+x,target,offset+row*stride,w);}
}
