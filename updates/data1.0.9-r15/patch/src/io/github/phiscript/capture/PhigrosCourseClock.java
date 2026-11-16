package io.github.phiscript.capture;

import io.github.phiscript.engine.*;
import io.github.phiscript.vision.*;
import java.util.*;

/** Predicts upcoming judge-line crossings from motion. Works with native timed
 * scripts, which contain judge positions but do not contain render trajectories. */
public final class PhigrosCourseClock {
    private final Chart chart;private final long hudAt;private final int width,height;private final Viewport physical;
    private long last=-1,epoch=-1,firstVote=-1,min,max;private int votes;
    private List<Sample> previous=Collections.emptyList();
    private static final class Sample {int kind,seen;double x,y,d,nx,ny,lx,ly,velocity;}
    private static final class Crossing {int kind;double x,y,at;Crossing(int k,double x,double y,double t){kind=k;this.x=x;this.y=y;at=t;}}
    public PhigrosCourseClock(Chart chart,long hudAt,int width,int height,Viewport viewport){this.chart=chart;this.hudAt=hudAt;this.width=width;this.height=height;physical=viewport;}
    public long observe(VisualScene scene,long at,long now){
        if(at<=last||at>now||now-at>250)return -1;
        long gap=last<0?0:at-last;last=at;
        if(!scene.getReliable()||scene.getWidth()<=scene.getHeight()||scene.getNotes().size()>128||gap>250){previous=Collections.emptyList();reset();return -1;}
        List<Sample> samples=new ArrayList<>();List<Crossing> crossings=new ArrayList<>();boolean[] used=new boolean[previous.size()];
        for(VisualNoteBlob note:scene.getNotes()){
            if(note.getConfidence()<.8||note.getLineIndex()<0||note.getLineIndex()>=scene.getLines().size())continue;
            VisualJudgeLine line=scene.getLines().get(note.getLineIndex());Sample s=new Sample();s.kind=note.getKind();s.x=note.getX();s.y=note.getY();s.nx=-line.getDy();s.ny=line.getDx();s.lx=line.getX();s.ly=line.getY();
            if(s.kind==3){double a=Math.abs((s.x-note.getExtentX()-s.lx)*s.nx+(s.y-note.getExtentY()-s.ly)*s.ny),b=Math.abs((s.x+note.getExtentX()-s.lx)*s.nx+(s.y+note.getExtentY()-s.ly)*s.ny);s.x+=a<=b?-note.getExtentX():note.getExtentX();s.y+=a<=b?-note.getExtentY():note.getExtentY();}
            double signed=(s.x-s.lx)*s.nx+(s.y-s.ly)*s.ny;s.d=Math.abs(signed);if(signed<0){s.nx=-s.nx;s.ny=-s.ny;}s.seen=1;
            int index=-1;double best=scene.getHeight()*.25;
            for(int i=0;i<previous.size();i++)if(!used[i]){Sample p=previous.get(i);double distance=Math.hypot(s.x-p.x,s.y-p.y);
                if(p.kind==s.kind&&p.nx*s.nx+p.ny*s.ny>.995&&Math.hypot(s.lx-p.lx,s.ly-p.ly)<scene.getHeight()*.015&&distance<best){best=distance;index=i;}}
            if(index>=0&&gap>=10){used[index]=true;Sample p=previous.get(index);double speed=(p.d-s.d)*1000/gap;
                if(speed>scene.getHeight()*.08&&speed<scene.getHeight()*10){
                    s.seen=p.seen+1;s.velocity=speed;
                    boolean steady=p.velocity>0&&Math.abs(speed-p.velocity)<=Math.max(scene.getHeight()*.04,p.velocity*.20);
                    double until=s.d/speed*1000;
                    if(s.seen>=3&&steady&&until>=80&&until<=1600)crossings.add(new Crossing(s.kind,s.x-s.nx*s.d,s.y-s.ny*s.d,at+until));
                }
            }
            samples.add(s);
        }
        previous=samples;if(crossings.isEmpty()){reset();return -1;}
        crossings.sort((a,b)->Double.compare(a.at,b.at));if(crossings.size()>12)crossings=new ArrayList<>(crossings.subList(0,12));
        Viewport view=new Viewport(physical.getLeft()*scene.getWidth()/width,physical.getTop()*scene.getHeight()/height,physical.getWidth()*scene.getWidth()/width,physical.getHeight()*scene.getHeight()/height);
        long low=epoch<0?hudAt-1000:epoch-80,high=epoch<0?hudAt+8000:epoch+80;int step=epoch<0?10:4;
        long winner=-1;double error=Double.POSITIVE_INFINITY;List<double[]> candidates=new ArrayList<>();
        for(long candidate=low;candidate<=high;candidate+=step){double e=error(crossings,candidate,view,scene.getHeight(),epoch<0);candidates.add(new double[]{candidate,e});if(e<error){error=e;winner=candidate;}}
        if(winner<0||error>30){reset();return -1;}
        for(double[] alternative:candidates)if(Math.abs(alternative[0]-winner)>80&&alternative[1]<=error+8){reset();return -1;}
        if(firstVote<0||at-firstVote>900||Math.abs(winner-min)>24||Math.abs(winner-max)>24){firstVote=at;votes=0;min=max=winner;}
        votes++;min=Math.min(min,winner);max=Math.max(max,winner);
        if(votes>=3&&at-firstVote>=70){epoch=(min+max)/2;reset();return epoch;}return -1;
    }
    private double error(List<Crossing> observations,long candidate,Viewport view,int height,boolean initial){
        List<Note> notes=chart.getNotes();boolean firstMatched=false;Set<Integer> used=new HashSet<>();double total=0;
        for(Crossing observed:observations){
            double t=(observed.at-candidate)/1000;int low=0,high=notes.size();while(low<high){int mid=(low+high)>>>1;if(notes.get(mid).getTimeSeconds()<t-.09)low=mid+1;else high=mid;}
            int chosen=-1;double best=Double.POSITIVE_INFINITY;
            for(int i=low;i<notes.size()&&notes.get(i).getTimeSeconds()<=t+.09;i++){
                Note note=notes.get(i);if(note.getType()!=observed.kind||used.contains(i))continue;
                Point p=chart.position(note,note.getTimeSeconds(),view,true);if(p==null)continue;
                double distance=Math.hypot(p.getX()-observed.x,p.getY()-observed.y)/height;if(distance>.06)continue;
                double score=Math.abs(note.getTimeSeconds()-t)*1000+distance*250;
                if(score<best){best=score;chosen=i;}
            }
            if(chosen<0)return Double.POSITIVE_INFINITY;
            used.add(chosen);total+=best;if(notes.get(chosen).getTimeSeconds()<=chart.getFirstNoteSeconds()+.04)firstMatched=true;
        }
        // A startup fit must include the first chord, preventing a repeated
        // later beat from being mislabelled as the new song's origin.
        if(initial&&!firstMatched)return Double.POSITIVE_INFINITY;
        return total/observations.size();
    }
    private void reset(){firstVote=-1;votes=0;}
}
