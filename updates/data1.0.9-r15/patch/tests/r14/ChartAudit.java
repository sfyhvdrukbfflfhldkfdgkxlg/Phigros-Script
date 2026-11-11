package io.github.phiscript.input;

import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

public final class ChartAudit {
    static final Viewport VIEW=new Viewport(0,0,1000,600);
    static final GestureProfile FIRST=new GestureProfile(true,70,.12f,true,true,true);
    static int charts,notes,areas,rawBlocked,safeEscapes,unavailable,safeChecks,uncoveredDrags,drags,maxPeak;
    static byte[] read(InputStream source)throws IOException{
        try(InputStream input=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] data=new byte[32768];for(int n;(n=input.read(data))!=-1;)out.write(data,0,n);return out.toByteArray();
        }
    }
    static void audit(Chart chart,String name)throws Exception{
        TouchService svc=new TouchService();
        TouchSafety safety=TouchSafety.read(svc,1000,600,VIEW);
        long start=System.nanoTime();TapMod.begin(chart);
        GesturePlan plan=GesturePlan.obtain(chart,FIRST,VIEW,safety.bounds,true);
        int lost=0,blocked=0,escapes=0,passed=0;
        ArrayList<TouchEvent> live=new ArrayList<>();List<TouchEvent> timeline=plan.events;int next=0;
        for(Note note:chart.getNotes()){
            long h=ContactPlanner.hit(note);
            live.removeIf(e->e.getEndMs()<h-65);
            while(next<timeline.size()&&timeline.get(next).getStartMs()<=h+65)live.add(timeline.get(next++));
            TapMod.planning.set(true);Point raw;
            try{raw=chart.position(note,note.getTimeSeconds(),VIEW,false);}finally{TapMod.planning.remove();}
            boolean inside=BlockAreaSet.covers(chart,raw.getX(),raw.getY(),0,0,1000,600,note.getTimeSeconds());
            if(inside)blocked++;
            boolean caught=false;
            for(int dt:new int[]{0,-8,8,-16,16,24,32,40,48,56,60,-24,-32}){
                long t=h+dt;
                Point center,normal;
                TapMod.planning.set(true);
                try{center=chart.position(note,Math.max(0,t)/1000.0,VIEW,false);normal=chart.judgeNormal(note,Math.max(0,t)/1000.0,VIEW);}finally{TapMod.planning.remove();}
                for(TouchEvent e:live){
                    if(e.getStartMs()>t || e.getEndMs()<=t)continue;
                    Point p;
                    try{p=safety.point(plan,chart,e,t,VIEW,new int[]{1000,600},new boolean[1]);}
                    catch(TapMod.NoSafePoint ex){continue;}
                    if(BlockAreaSet.covers(chart,Math.round(p.getX()),Math.round(p.getY()),0,0,1000,600,t/1000.0))throw new AssertionError(name+" emits inside noise "+note.getId());
                    passed++;
                    double dist=Math.abs((p.getX()-center.getX())*normal.getY()-(p.getY()-center.getY())*normal.getX())/Math.hypot(normal.getX(),normal.getY());
                    if(dist<=ContactPlanner.radius(VIEW)+1.5){caught=true;break;}
                }
                if(caught)break;
            }
            if(note.getType()==2){drags++;if(!caught)lost++;}
            if(inside&&caught)escapes++;
        }
        List<long[]> changes=new ArrayList<>();
        for(TouchEvent e:timeline)if(e.getNote().getType()==2){changes.add(new long[]{e.getStartMs(),1});changes.add(new long[]{e.getEndMs(),-1});}
        changes.sort((a,b)->a[0]==b[0]?Long.compare(a[1],b[1]):Long.compare(a[0],b[0]));
        int liveCount=0,peak=0;for(long[] event:changes){liveCount+=event[1];peak=Math.max(peak,liveCount);}
        if(peak>6 || lost!=0 || plan.unavailableNotes!=0 || escapes!=blocked)
            throw new AssertionError(name+" peak="+peak+" unmatchedYellow="+lost+" unavailable="+plan.unavailableNotes+" safeNearHit="+escapes+"/"+blocked);
        charts++;notes+=chart.getNotes().size();areas+=BlockAreaSet.count(chart);rawBlocked+=blocked;safeEscapes+=escapes;unavailable+=plan.unavailableNotes;
        safeChecks+=passed;uncoveredDrags+=lost;maxPeak=Math.max(maxPeak,peak);
        System.out.println(name+" notes="+chart.getNotes().size()+" zones="+BlockAreaSet.count(chart)+" peak="+peak+" blockedHeads="+blocked+" safeNearHit="+escapes+" unavailable="+plan.unavailableNotes+" unmatchedYellow="+lost+" seconds="+((System.nanoTime()-start)/1e9));
    }
    public static void main(String[] args)throws Exception{
        android.content.Context.root=Files.createTempDirectory("r14charts").toFile();ContactRegression.clear();
        io.github.phiscript.AppSettings.edges=true;
        if(Arrays.asList(args).contains("--random"))io.github.phiscript.AppSettings.values.put("tapmod_enabled","true");
        if(args[0].endsWith(".pez")){
            PhiraImportData d=PhiraImportDecoder.INSTANCE.decode(new FileInputStream(args[0]),"Desultory_Signals_AT.pez");
            audit(SharedChartImport.parseJson(d.getNormalizedJson()),d.getTitle()+" "+d.getLevel());
        }else try(ZipFile z=new ZipFile(args[0])){
            JSONObject catalog=new JSONObject(new String(read(z.getInputStream(z.getEntry("swordalt-catalog.json"))),StandardCharsets.UTF_8));
            JSONArray entries=catalog.getJSONArray("entries");
            for(int i=0;i<entries.length();i++){
                JSONObject row=entries.getJSONObject(i);
                if(row.getInt("noiseCount")==0)continue;
                String path=row.getString("path");
                audit(SharedChartImport.parseJson(new String(read(z.getInputStream(z.getEntry("charts/"+path))),StandardCharsets.UTF_8)),path);
            }
        }
        System.out.println("TOTAL random="+Arrays.asList(args).contains("--random")+" charts="+charts+" notes="+notes+" areas="+areas+" yellows="+drags+" yellowPeak="+maxPeak+" initiallyBlocked="+rawBlocked+" safeNearHit="+safeEscapes+" unavailable="+unavailable+" uncheckedYellow="+uncoveredDrags+" safeObservations="+safeChecks);
    }
}
