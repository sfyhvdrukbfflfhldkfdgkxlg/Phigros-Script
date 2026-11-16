package io.github.phiscript.record;

import java.nio.ByteBuffer;
public final class VideoPolicyTest {
    public static void main(String[] args){
        int[] landscape=RecordingPolicy.size(2400,1080),portrait=RecordingPolicy.size(720,1280);
        require(landscape[0]==1920&&landscape[1]==1080&&portrait[0]==1080&&portrait[1]==1920);
        require(RecordingPolicy.FPS==60&&RecordingPolicy.BIT_RATE==16000000);
        require(Math.abs(RecordingPolicy.captureScale(2400,1080,1280,true)-.8)<1e-12);
        require(Math.abs(RecordingPolicy.captureScale(2400,1080,1280,false)-1280.0/2400)<1e-12);
        long prior=-1;
        for(long i=0;i<=216000;i++){
            long ns=RecordingPolicy.presentationNs(i);require(ns>prior);require(RecordingPolicy.sourceSlot(ns)==i);
            long due=RecordingPolicy.dueMs(i);require(due*1000000L>=ns&&(due-1)*1000000L<ns);prior=ns;
        }
        require(prior==3600000000000L);
        ByteBuffer source=ByteBuffer.allocate(23),dest=ByteBuffer.allocate(16);
        for(int i=0;i<23;i++)source.put((byte)i);source.flip();source.position(3);
        FramePixels.copyRows(source,12,2,2,dest);
        for(int i=0;i<8;i++){require(dest.get(i)==3+i);require(dest.get(8+i)==15+i);}
        boolean failed=false;try{FramePixels.copyRows(ByteBuffer.allocate(10),12,2,2,dest);}catch(IllegalArgumentException expected){failed=true;}require(failed);
        System.out.println("PASS 1080p landscape/portrait, bounded capture, padded RGBA rows and 216001 frames on a 60fps clock (host model).");
    }
    static void require(boolean result){if(!result)throw new AssertionError();}
}
