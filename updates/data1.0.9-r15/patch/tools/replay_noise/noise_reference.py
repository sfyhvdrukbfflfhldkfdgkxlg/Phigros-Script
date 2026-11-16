"""Independent PGR noise reference based on Phira-Pro 3776f5f core/block.rs.

Native outgoing-key easing, accumulated anchor transforms, inversion parity and
screen-height inset; input coordinates use v5 Y-down. No production imports.
"""
import math,bisect
ASPECT=16/9;W=1600;H=W/ASPECT
AREAS=[]

def ease_raw(u,e):
 if e==13:return 0.
 if e==14:return 1.
 if e==0:return u
 p=(e-1)//3+2;m=(e-1)%3
 return u**p if m==0 else 1-(1-u)**p if m==1 else (2*u)**p/2 if u<.5 else 1-(2-2*u)**p/2
TABLE=[[ease_raw(k/100,e) for k in range(101)] for e in range(15)]
def ease(u,e):
 x=max(0,min(1,u))*100;k=int(x)
 return TABLE[e][100] if k>=100 else TABLE[e][k]+(TABLE[e][k+1]-TABLE[e][k])*(x-k)
class Track:
 def __init__(self,events,field,default):
  self.events=events;self.ts=[e['time'] for e in events];self.v=[];self.anchors=[];self.eases=[];self.default=default
  for e in events:
   v=e.get(field,default)
   if isinstance(v,dict):v=(v['x'],v['y'])
   else:v=(v,v)
   self.v.append(v);a=e.get('anchor');self.anchors.append((a['x']*W,a['y']*H) if a else None);self.eases.append((e.get('easeTypeX',e.get('easeType',0)),e.get('easeTypeY',e.get('easeType',0))))
 def at(self,t):return bisect.bisect_right(self.ts,t)-1
 def value(self,t):
  i=self.at(t)
  if i<0:return self.default
  if i+1>=len(self.ts):return self.v[i]
  u=(t-self.ts[i])/(self.ts[i+1]-self.ts[i]);return tuple(a+(b-a)*ease(u,e) for a,b,e in zip(self.v[i],self.v[i+1],self.eases[i]))
class Area:
 def __init__(self,a,index):
  self.index=index;bl=a['bottomLeftPercentage'];tr=a['topRightPercentage'];self.l=min(bl['x'],tr['x']);self.r=max(bl['x'],tr['x']);self.b=min(bl['y'],tr['y']);self.t=max(bl['y'],tr['y']);self.cx=(self.l+self.r)/2;self.cy=(self.b+self.t)/2;self.on=max(a.get('appearTime',0),a.get('enableTime',0));self.off=min(a.get('disappearTime',0),a.get('disableTime',0));self.sub=a.get('isSubtract',False)
  self.move=Track(a.get('moveEvents',[]),'endPosition',(self.cx,self.cy));self.scale=Track(a.get('scaleEvents',[]),'scale',(1,1));self.rotate=Track(a.get('rotateEvents',[]),'rotation',(0,0))
 def shape(self,t):
  sx,sy=self.scale.value(t);angle=self.rotate.value(t)[0];x,y=self.cx*W,self.cy*H;last=self.scale.at(t)
  for k in range(last+1):
   if k+1>=len(self.scale.ts):break
   nx,ny=self.scale.v[k+1] if k<last else (sx,sy);ax,ay=self.scale.anchors[k] or (self.cx*W,self.cy*H);ox,oy=self.scale.v[k];x=ax+(x-ax)*(nx/ox if abs(ox)>1e-38 else 1);y=ay+(y-ay)*(ny/oy if abs(oy)>1e-38 else 1)
  last=self.rotate.at(t)
  for k in range(last+1):
   if k+1>=len(self.rotate.ts):break
   delta=math.radians((self.rotate.v[k+1][0] if k<last else angle)-self.rotate.v[k][0]);ax,ay=self.rotate.anchors[k] or (self.cx*W,self.cy*H);dx,dy=x-ax,y-ay;c,s=math.cos(delta),math.sin(delta);x=ax+dx*c-dy*s;y=ay+dx*s+dy*c
  mx,my=self.move.value(t);x+=(mx-self.cx)*W;y+=(my-self.cy)*H;angle=math.radians(angle)
  return x,y,abs(sx)*(self.r-self.l)*W/2,abs(sy)*(self.t-self.b)*H/2,math.cos(angle),math.sin(angle)
 def contains(self,shape,x,y,inset):
  cx,cy,hx,hy,c,s=shape;dx=x-cx;dy=y-cy;lx=dx*c+dy*s;ly=-dx*s+dy*c
  if hx<1e-7 or hy<1e-7:return False
  margin=H*.05*(1 if self.sub else -1) if inset else 0
  return abs(lx)<=max(0,hx+margin) and abs(ly)<=max(0,hy+margin)
def prepare(raw,aspect):
 global ASPECT,H,AREAS
 ASPECT=aspect;H=W/aspect
 AREAS=[Area(a,i) for i,a in enumerate(raw.get('blockAreaList',raw.get('BlockAreaList',[])))]
def checker(t,points):
 active=[(a,a.shape(t)) for a in AREAS if a.on<=t<a.off];out=[]
 for ident,p in points.items():
  x=(p['x']+1)*W/2;y=(1-p['y']*ASPECT)*H/2
  fn=False;fs=0;inn=False;ins=0;ids=[]
  for a,s in active:
   f=a.contains(s,x,y,False);i=a.contains(s,x,y,True)
   if f:ids.append(a.index)
   if a.sub:fs+=f;ins+=i
   else:fn|=f;inn|=i
  if (fn^(fs%2==1)) and (inn^(ins%2==1)):out.append((ident,ids))
 return out
