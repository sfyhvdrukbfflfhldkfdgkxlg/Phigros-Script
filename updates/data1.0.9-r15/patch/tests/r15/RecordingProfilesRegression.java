package io.github.phiscript.record;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Handler;
import android.os.SystemClock;

/** Exercises production sampling/clock/session code with bounded Image/encoder models. */
public final class RecordingProfilesRegression {
    static void require(boolean result,String why){if(!result)throw new AssertionError(why);}
    public static void main(String[] args){
        Context owner=new Context();SystemClock.now=1000;
        for(int edge:new int[]{720,1080})for(int fps:new int[]{30,60}){
            RecordingPolicy.Profile profile=RecordingPolicy.profile(edge,fps);
            int[] landscape=RecordingPolicy.size(2400,1080,profile),portrait=RecordingPolicy.size(1080,2400,profile);
            require(landscape[1]==edge&&landscape[0]==edge*16/9&&portrait[0]==edge&&portrait[1]==landscape[0],"profile dimensions/orientation");
            require(profile.bitRate==(edge==720?4000000:8000000)*(fps==60?2:1),"profile bitrate");
            for(long frame=0;frame<=fps*3600;frame++){
                long ns=RecordingPolicy.presentationNs(frame,profile),due=RecordingPolicy.dueMs(frame,profile);
                require(RecordingPolicy.sourceSlot(ns,profile)==frame,"source slot disagrees with output clock");
                require(due*1000000L>=ns&&(due-1)*1000000L<ns,"fractional deadline rounded early");
            }
            require(RecordingPolicy.presentationNs(fps*3600,profile)==3600000000000L,"one-hour timestamp drift");
            RecordingPreferences.save(owner,profile);
            RecordingPolicy.Profile persisted=RecordingPreferences.load(new Context());
            require(persisted.shortEdge==edge&&persisted.fps==fps,"persisted profile");
            CaptureVideo.begin(owner);
            double capture=CaptureVideo.captureScale(owner,2400,1080,1280);
            require(Math.abs(capture-(edge==720?1280.0/2400:.8))<1e-12,"capture resolution disagrees with selected tier");
            require(CaptureVideo.captureScale(owner,2400,1080,2000)>=2000.0/2400,"lower tier degrades OCR resolution");
            RecordingPreferences.save(owner,RecordingPolicy.DEFAULT); // A running/armed session keeps its snapshot.
            CaptureVideo.playing();BitmapVideoEncoder.pts.clear();BitmapVideoEncoder.pixels.clear();
            long began=SystemClock.now;
            for(int frame=0;frame<fps;frame++){
                Handler.until(began+RecordingPolicy.dueMs(frame,profile));
                CaptureVideo.image(owner,new Image(1000000+RecordingPolicy.presentationNs(frame,profile),frame));
                Handler.until(began+RecordingPolicy.dueMs(frame,profile));
            }
            Handler.until(began+1000);
            require(BitmapVideoEncoder.pts.size()==fps+1,"output frame count changed during session");
            require(BitmapVideoEncoder.selected.fps==fps&&BitmapVideoEncoder.size[1]==edge,"codec profile disagrees with session clock");
            for(int frame=0;frame<=fps;frame++)require(BitmapVideoEncoder.pts.get(frame)==RecordingPolicy.presentationNs(frame,profile),"submitted timestamp disagrees with selected frame rate");
            CaptureVideo.stop(owner);Handler.until(began+1100);
            require(BitmapVideoEncoder.saved&&Bitmap.live==0,"profile session leaked frames or did not finalize");
        }
        RecordingPolicy.Profile fallback=RecordingPolicy.stored("bad","120");
        require(fallback.shortEdge==1080&&fallback.fps==60,"invalid persisted settings broke default");
        boolean rejected=false;try{RecordingPolicy.profile(2160,120);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"unsupported profile accepted");
        System.out.println("PASS four 720p/1080p x 30/60fps profiles: dimensions, bitrate, one-hour output/source clocks, persisted settings, immutable active session, OCR resolution floor and capture/encoder model cleanup.");
    }
}
