package android.graphics;
public class RectF {public float left,top,right,bottom;public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public RectF(RectF x){this(x.left,x.top,x.right,x.bottom);}public float width(){return right-left;}public float height(){return bottom-top;}}
