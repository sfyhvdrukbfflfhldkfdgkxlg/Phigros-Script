package io.github.phiscript.capture;
import android.graphics.Bitmap;import io.github.phiscript.vision.NormalizedPoint;import java.util.*;
/** Find a three-control row by components and retry/play shapes; no hard-coded Phira coordinates. */
final class PhigrosPauseMenu {
 private int[] row=new int[0],gray=new int[0],dark=new int[0],queue=new int[0];private boolean[] seen=new boolean[0];private int width,height;
 private static final class Icon {float x,y,size;boolean retry,resume,back;Icon(float x,float y,float size,boolean retry,boolean resume,boolean back){this.x=x;this.y=y;this.size=size;this.retry=retry;this.resume=resume;this.back=back;}}
 NormalizedPoint locate(Bitmap bitmap){
  if(bitmap.isRecycled()||bitmap.getWidth()<=bitmap.getHeight())return null;
  width=Math.min(560,bitmap.getWidth());height=Math.max(1,Math.round((float)bitmap.getHeight()*width/bitmap.getWidth()));int count=width*height;
  if(gray.length!=count){gray=new int[count];dark=new int[count];queue=new int[count];seen=new boolean[count];}if(row.length!=bitmap.getWidth())row=new int[bitmap.getWidth()];
  Arrays.fill(gray,0);Arrays.fill(dark,255);
  // Preserve thin open-circle/chevron strokes. Nearest-neighbour sampling broke
  // the real 2-pixel retry ring into separate components on a 1536-wide capture.
  for(int y=0;y<bitmap.getHeight();y++){bitmap.getPixels(row,0,row.length,0,y,row.length,1);int yy=Math.min(height-1,y*height/bitmap.getHeight());
   for(int x=0;x<row.length;x++){int c=row[x],r=(c>>>16)&255,g=(c>>>8)&255,b=c&255,l=(r*77+g*150+b*29)>>>8,i=yy*width+Math.min(width-1,x*width/row.length);gray[i]=Math.max(gray[i],l);dark[i]=Math.min(dark[i],l);}}
  NormalizedPoint found=null;
  for(int polarity=0;polarity<2;polarity++)for(int level:new int[]{150,205}){
   List<Icon> icons=components(polarity,level);
   for(Icon retry:icons)if(retry.retry)for(Icon resume:icons)if(resume.resume&&resume.x>retry.x){float gap=resume.x-retry.x;
    if(gap<width*.045f||gap>width*.34f||Math.abs(resume.y-retry.y)>Math.max(3,Math.min(retry.size,resume.size)*.22f)||!similar(retry,resume))continue;
    for(Icon back:icons){float left=retry.x-back.x;
     if(!back.back||left<=0||Math.abs(left-gap)>gap*.30f||!similar(back,retry)||Math.abs(back.y-retry.y)>Math.max(3,Math.min(back.size,retry.size)*.22f))continue;
     NormalizedPoint point=new NormalizedPoint(retry.x/width,retry.y/height);if(found!=null&&Math.hypot(found.getX()-point.getX(),found.getY()-point.getY())>.035)return null;found=point;}
   }
  }return found;
 }
 private static boolean similar(Icon a,Icon b){return a.size>=b.size*.48f&&a.size<=b.size*2.1f;}
 private List<Icon> components(int polarity,int level){
  Arrays.fill(seen,false);List<Icon> result=new ArrayList<>();int x0=(int)(width*.08f),x1=(int)(width*.92f),y0=(int)(height*.18f),y1=(int)(height*.82f);
  for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int start=y*width+x;if(seen[start]||!lit(start,polarity,level))continue;
   int head=0,tail=0,minX=x,maxX=x,minY=y,maxY=y;queue[tail++]=start;seen[start]=true;
   while(head<tail){int at=queue[head++],px=at%width,py=at/width;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=px+dx,ny=py+dy;if(nx<x0||nx>=x1||ny<y0||ny>=y1)continue;int n=ny*width+nx;if(!seen[n]&&lit(n,polarity,level)){seen[n]=true;queue[tail++]=n;}}}
   int w=maxX-minX+1,h=maxY-minY+1;if(h<Math.max(5,height*.022f)||h>height*.24f||w<h*.30f||w>h*2||tail<12||tail>w*h*.94f)continue;
   float size=Math.max(w,h),cx=(minX+maxX)*.5f,cy=(minY+maxY)*.5f;boolean retry=false,resume=false;
   for(float factor:new float[]{1.30f,1.40f,1.50f,1.62f})for(float offset:new float[]{-.035f,0,.035f}){boolean[] mask=sample(cx+size*factor*offset,cy,size*factor,polarity,level);retry|=ring(mask);resume|=triangle(mask);}
   boolean[] shapes=shapes(minX,minY,w,h,polarity,level);
   retry|=shapes[0];resume|=shapes[1];
   result.add(new Icon(cx,cy,size,retry,resume,shapes[2]));if(result.size()>160)return new ArrayList<>();
  }return result;
 }
 private boolean lit(int i,int polarity,int level){return (polarity==0?gray[i]:255-dark[i])>=level;}
 private boolean[] shapes(int left,int top,int w,int h,int polarity,int level){
  int total=0,center=0,centerLit=0,annulus=0,triHit=0,triUnion=0,backHit=0;boolean[] sectors=new boolean[12];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   float u=x/(float)Math.max(1,w-1),v=y/(float)Math.max(1,h-1),dx=(x-(w-1)*.5f)/Math.max(w,h),dy=(y-(h-1)*.5f)/Math.max(w,h);double radius=Math.hypot(dx,dy);
   boolean on=lit((top+y)*width+left+x,polarity,level),tri=u<=1-2*Math.abs(v-.5f)+.06f;
   if(tri||on)triUnion++;if(tri&&on)triHit++;if(radius<.20){center++;if(on)centerLit++;}
   if(on){total++;if(radius>=.28&&radius<=.62){annulus++;int sector=(int)((Math.atan2(dy,dx)+Math.PI)*12/(2*Math.PI));sectors[Math.min(11,sector)]=true;}if(Math.abs(u-2*Math.abs(v-.5f))<.24)backHit++;}
  }
  int occupied=0;for(boolean value:sectors)if(value)occupied++;
  boolean circle=w>=h*.68&&w<=h*1.5&&total<w*h*.52&&centerLit<=Math.max(1,center*.12)&&annulus>=total*.70&&occupied>=9;
  boolean play=w>=h*.45&&w<=h*1.6&&total>w*h*.30&&triHit>triUnion*.68;
  boolean back=w>=h*.30&&w<=h*.90&&total<w*h*.58&&backHit>=total*.78;
  return new boolean[]{circle,play,back};
 }
 private boolean[] sample(float cx,float cy,float size,int polarity,int level){boolean[] out=new boolean[1089];for(int y=0;y<33;y++)for(int x=0;x<33;x++){int px=Math.round(cx+(x/32f-.5f)*size),py=Math.round(cy+(y/32f-.5f)*size);out[y*33+x]=px>=0&&px<width&&py>=0&&py<height&&lit(py*width+px,polarity,level);}return out;}
 private static boolean triangle(boolean[] m){int intersection=0,union=0;for(int i=0;i<m.length;i++){float x=(i%33)/32f,y=(i/33)/32f;boolean e=x>=.20f&&x<=.85f&&Math.abs(y-.5f)<=(.85f-x)*.58f;if(e&&m[i])intersection++;if(e||m[i])union++;}return union>0&&intersection>union*.57f;}
 private static boolean ring(boolean[] m){int center=0,centerLit=0,ring=0,ringLit=0,total=0,outer=0,outerLit=0;for(int i=0;i<m.length;i++){float x=(i%33)/32f-.5f,y=(i/33)/32f-.5f,r=(float)Math.hypot(x,y);if(m[i])total++;if(r<.13f){center++;if(m[i])centerLit++;}if(r>=.265f&&r<=.335f){ring++;if(m[i])ringLit++;}if(r>.43f){outer++;if(m[i])outerLit++;}}return total>=m.length*.105f&&total<=m.length*.43f&&centerLit<=center*.12f&&ringLit>=ring*.56f&&outerLit<=outer*.12f;}
}
