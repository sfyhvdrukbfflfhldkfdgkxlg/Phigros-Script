package io.github.phiscript.capture;

import io.github.phiscript.engine.*;
import io.github.phiscript.vision.*;
import java.util.*;

/** Fits the chart's rendered approaching notes to fresh game images, not HUD appearance time. */
public final class PhigrosChartClock {
    private final Chart chart;private final long hudAt;private final int screenWidth,screenHeight;private final Viewport physical;
    private long last=-1,first=-1,minEpoch,maxEpoch;private int votes;private long epoch=-1;
    private long previousAt=-1;private List<Sample> previous=Collections.emptyList();
    private static final class Sample {int kind;float x,y,distance;Sample(int kind,float x,float y){this.kind=kind;this.x=x;this.y=y;}}
    private static final class Fit {long epoch;double error;Fit(long epoch,double error){this.epoch=epoch;this.error=error;}}
    public PhigrosChartClock(Chart chart,long hudAt,int width,int height,Viewport physical){this.chart=chart;this.hudAt=hudAt;screenWidth=width;screenHeight=height;this.physical=physical;}
    public long observe(VisualScene scene,long capturedAt,long now){
        if(epoch>=0)return epoch;if(capturedAt<=last||capturedAt>now||now-capturedAt>250)return -1;last=capturedAt;
        if(!scene.getReliable()||scene.getWidth()<=scene.getHeight()||scene.getNotes().isEmpty()){reset();previous=Collections.emptyList();previousAt=-1;return -1;}
        Viewport viewport=new Viewport(physical.getLeft()*scene.getWidth()/screenWidth,physical.getTop()*scene.getHeight()/screenHeight,physical.getWidth()*scene.getWidth()/screenWidth,physical.getHeight()*scene.getHeight()/screenHeight);
        List<Sample> observed=new ArrayList<>();
        for(VisualNoteBlob note:scene.getNotes()){
            if(note.getConfidence()<.75||note.getLineIndex()<0||note.getLineIndex()>=scene.getLines().size())continue;
            double x=note.getX(),y=note.getY();
            if(note.getKind()==3){VisualJudgeLine line=scene.getLines().get(note.getLineIndex());double ax=x-note.getExtentX(),ay=y-note.getExtentY(),bx=x+note.getExtentX(),by=y+note.getExtentY();
                double a=Math.abs((ax-line.getX())*-line.getDy()+(ay-line.getY())*line.getDx()),b=Math.abs((bx-line.getX())*-line.getDy()+(by-line.getY())*line.getDx());x=a<=b?ax:bx;y=a<=b?ay:by;}
            if(x<viewport.getLeft()||x>=viewport.getRight()||y<viewport.getTop()||y>=viewport.getBottom())continue;
            Sample sample=new Sample(note.getKind(),(float)x,(float)y);VisualJudgeLine line=scene.getLines().get(note.getLineIndex());sample.distance=(float)Math.abs((x-line.getX())*-line.getDy()+(y-line.getY())*line.getDx());observed.add(sample);
        }
        List<Sample> moving=new ArrayList<>();
        if(previousAt>=0&&capturedAt-previousAt<=250){
            boolean[] used=new boolean[previous.size()];double dt=(capturedAt-previousAt)/1000.0,range=scene.getHeight()*(.04+12*dt);
            for(Sample current:observed){int selected=-1;double best=range*range;
                for(int i=0;i<previous.size();i++)if(!used[i]&&previous.get(i).kind==current.kind){Sample old=previous.get(i);double d=(old.x-current.x)*(old.x-current.x)+(old.y-current.y)*(old.y-current.y);if(d<best){best=d;selected=i;}}
                if(selected>=0){used[selected]=true;double advance=previous.get(selected).distance-current.distance;if(advance>scene.getHeight()*.0005&&advance<=scene.getHeight()*12*dt)moving.add(current);}
            }
        }
        previous=observed;previousAt=capturedAt;observed=moving;
        if(observed.isEmpty()){reset();return -1;}observed.sort((a,b)->Float.compare(a.distance,b.distance));if(observed.size()>12)observed=new ArrayList<>(observed.subList(0,12));
        List<Fit> fits=new ArrayList<>();
        long lower=hudAt-1000,upper=Math.min(capturedAt,hudAt+8000);
        for(long candidate=lower;candidate<=upper;candidate+=20){double time=(capturedAt-candidate)/1000.0;fits.add(new Fit(candidate,error(observed,viewport,time,scene.getHeight())));}
        fits.sort((a,b)->Double.compare(a.error,b.error));if(fits.isEmpty()||!Double.isFinite(fits.get(0).error)){reset();return -1;}
        Fit best=fits.get(0);
        for(int i=0;i<Math.min(3,fits.size());i++){Fit coarse=fits.get(i);for(long candidate=coarse.epoch-20;candidate<=coarse.epoch+20;candidate+=2){if(candidate<lower||candidate>upper)continue;Fit f=new Fit(candidate,error(observed,viewport,(capturedAt-candidate)/1000.0,scene.getHeight()));if(f.error<best.error)best=f;}}
        if(best.error>scene.getHeight()*.025){reset();return -1;}
        for(Fit alternative:fits)if(Math.abs(alternative.epoch-best.epoch)>=120&&alternative.error<=best.error+scene.getHeight()*.004){reset();return -1;}
        if(first<0||Math.abs(best.epoch-minEpoch)>32||Math.abs(best.epoch-maxEpoch)>32||capturedAt-first>900){first=capturedAt;votes=0;minEpoch=maxEpoch=best.epoch;}
        minEpoch=Math.min(minEpoch,best.epoch);maxEpoch=Math.max(maxEpoch,best.epoch);votes++;
        if(votes>=3&&capturedAt-first>=70){epoch=(minEpoch+maxEpoch)/2;return epoch;}
        return -1;
    }
    private void reset(){first=-1;votes=0;}
    private double error(List<Sample> observed,Viewport viewport,double seconds,int height){
        if(seconds<0)return Double.POSITIVE_INFINITY;
        List<Note> notes=chart.getNotes();int low=0,high=notes.size();while(low<high){int mid=(low+high)>>>1;if(notes.get(mid).getTimeSeconds()<seconds-.08)low=mid+1;else high=mid;}
        List<Sample> expected=new ArrayList<>();
        for(int i=low;i<notes.size()&&expected.size()<48&&i<low+512;i++){
            Note note=notes.get(i);if(note.getTimeSeconds()>seconds+6)break;
            Point p=chart.position(note,seconds,viewport,true);if(p==null||!viewport.contains(p))continue;
            expected.add(new Sample(note.getType(),p.getX(),p.getY()));
        }
        if(expected.isEmpty())return Double.POSITIVE_INFINITY;
        boolean[] usedA=new boolean[observed.size()],usedB=new boolean[expected.size()];double sum=0;int matches=0;
        for(int k=0;k<observed.size();k++){
            double best=height*height*.25*.25;int a=-1,b=-1;
            for(int i=0;i<observed.size();i++)if(!usedA[i])for(int j=0;j<expected.size();j++)if(!usedB[j]&&observed.get(i).kind==expected.get(j).kind){Sample x=observed.get(i),y=expected.get(j);double d=(x.x-y.x)*(x.x-y.x)+(x.y-y.y)*(x.y-y.y);if(d<best){best=d;a=i;b=j;}}
            if(a<0)break;usedA[a]=true;usedB[b]=true;sum+=best;matches++;
        }
        if(matches<Math.min(2,observed.size())||matches<observed.size()*.6)return Double.POSITIVE_INFINITY;
        sum+=(observed.size()-matches)*height*height*.04*.04;
        // An advanced beat-period lookalike predicts extra earlier notes that are
        // not in the image. Penalize those instead of selecting a later repeated beat.
        for(int i=0;i<Math.min(3,expected.size());i++)if(!usedB[i])sum+=height*height*.045*.045;
        return Math.sqrt(sum/Math.max(1,matches));
    }
}
