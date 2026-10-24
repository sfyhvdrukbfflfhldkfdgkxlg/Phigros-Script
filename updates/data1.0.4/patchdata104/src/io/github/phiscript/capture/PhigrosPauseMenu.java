package io.github.phiscript.capture;
import android.graphics.Bitmap;import io.github.phiscript.vision.NormalizedPoint;import java.util.*;
/** Find a three-control row by components and retry/play shapes; no hard-coded Phira coordinates. */
final class PhigrosPauseMenu {
 private int[] row=new int[0],gray=new int[0],queue=new int[0];private boolean[] seen=new boolean[0];private int width,height;
 private static final class Icon {float x,y,size;boolean retry,resume;Icon(float x,float y,float size,boolean retry,boolean resume){this.x=x;this.y=y;this.size=size;this.retry=retry;this.resume=resume;}}
 NormalizedPoint locate(Bitmap bitmap){
  if(bitmap.isRecycled()||bitmap.getWidth()<=bitmap.getHeight())return null;
  width=Math.min(560,bitmap.getWidth());height=Math.max(1,Math.round((float)bitmap.getHeight()*width/bitmap.getWidth()));int count=width*height;
  if(gray.length!=count){gray=new int[count];queue=new int[count];seen=new boolean[count];}if(row.length!=bitmap.getWidth())row=new int[bitmap.getWidth()];
  for(int y=0;y<height;y++){bitmap.getPixels(row,0,row.length,0,Math.min(bitmap.getHeight()-1,y*bitmap.getHeight()/height),row.length,1);
   for(int x=0;x<width;x++){int c=row[Math.min(row.length-1,x*row.length/width)],r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;gray[y*width+x]=(r*77+g*150+b*29)>>>8;}}
  NormalizedPoint found=null;
  for(int polarity=0;polarity<2;polarity++)for(int level:new int[]{150,205}){
   List<Icon> icons=components(polarity,level);
   for(Icon retry:icons)if(retry.retry)for(Icon resume:icons)if(resume.resume&&resume.x>retry.x){float gap=resume.x-retry.x;
    if(gap<width*.045f||gap>width*.34f||Math.abs(resume.y-retry.y)>Math.max(3,Math.min(retry.size,resume.size)*.22f)||!similar(retry,resume))continue;
    for(Icon back:icons){float left=retry.x-back.x;
     if(left<=0||Math.abs(left-gap)>gap*.30f||!similar(back,retry)||Math.abs(back.y-retry.y)>Math.max(3,Math.min(back.size,retry.size)*.22f))continue;
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
   result.add(new Icon(cx,cy,size,retry,resume));if(result.size()>160)return new ArrayList<>();
  }return result;
 }
 private boolean lit(int i,int polarity,int level){return (polarity==0?gray[i]:255-gray[i])>=level;}
 private boolean[] sample(float cx,float cy,float size,int polarity,int level){boolean[] out=new boolean[1089];for(int y=0;y<33;y++)for(int x=0;x<33;x++){int px=Math.round(cx+(x/32f-.5f)*size),py=Math.round(cy+(y/32f-.5f)*size);out[y*33+x]=px>=0&&px<width&&py>=0&&py<height&&lit(py*width+px,polarity,level);}return out;}
 private static boolean triangle(boolean[] m){int intersection=0,union=0;for(int i=0;i<m.length;i++){float x=(i%33)/32f,y=(i/33)/32f;boolean e=x>=.20f&&x<=.85f&&Math.abs(y-.5f)<=(.85f-x)*.58f;if(e&&m[i])intersection++;if(e||m[i])union++;}return union>0&&intersection>union*.57f;}
 private static boolean ring(boolean[] m){int center=0,centerLit=0,ring=0,ringLit=0,total=0,outer=0,outerLit=0;for(int i=0;i<m.length;i++){float x=(i%33)/32f-.5f,y=(i/33)/32f-.5f,r=(float)Math.hypot(x,y);if(m[i])total++;if(r<.13f){center++;if(m[i])centerLit++;}if(r>=.265f&&r<=.335f){ring++;if(m[i])ringLit++;}if(r>.43f){outer++;if(m[i])outerLit++;}}return total>=m.length*.105f&&total<=m.length*.43f&&centerLit<=center*.12f&&ringLit>=ring*.56f&&outerLit<=outer*.12f;}
}
