
package android.graphics;
public class Path {
 public float firstX,firstY,lastX,lastY; public boolean empty=true;
 public final java.util.List<float[]> points=new java.util.ArrayList<>();
 public void moveTo(float x,float y){if(!empty)throw new AssertionError("multiple contours");firstX=lastX=x;firstY=lastY=y;empty=false;points.add(new float[]{x,y});}
 public void lineTo(float x,float y){lastX=x;lastY=y;points.add(new float[]{x,y});}
}
