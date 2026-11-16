package android.graphics;
public class RectF {
 public float left,top,right,bottom;
 public RectF(){}public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public RectF(RectF v){this(v.left,v.top,v.right,v.bottom);}
 public float width(){return right-left;}public float height(){return bottom-top;}public float centerX(){return (left+right)/2;}public float centerY(){return (top+bottom)/2;}
 public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
 public boolean equals(Object o){if(!(o instanceof RectF))return false;RectF r=(RectF)o;return left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;}
 public int hashCode(){return Float.floatToIntBits(left)^Float.floatToIntBits(top)^Float.floatToIntBits(right)^Float.floatToIntBits(bottom);}
}
