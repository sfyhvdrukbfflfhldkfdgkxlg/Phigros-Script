package io.github.phiscript.root;
import android.content.Context;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class RootWarmupRegression {
    public static void main(String[] args)throws Exception{
        RootClient.after=null;RootClient.failAt=-1;
        RootWarmup warm=new RootWarmup(new Context(),1000,600);Thread.sleep(230);
        if(RootClient.last.frames<2)throw new AssertionError("no idle heartbeat");
        warm.tap(.5f,.5f,2,80,new AtomicBoolean());
        if(RootClient.last.heads.size()!=2||!RootClient.last.active.isEmpty())throw new AssertionError("menu taps not released");
        if(!RootClient.last.physicalHeads.get(0).equals(RootClient.last.physicalHeads.get(1)))throw new AssertionError("menu double tap changed pointer identity");
        RootClient client=warm.take();int at=client.frames;Thread.sleep(180);
        if(client.frames!=at)throw new AssertionError("heartbeat continued after ownership transfer");
        client.frame(Arrays.asList(new RootTouchState.Sample(1,100,300)));Thread.sleep(140);
        if(client.active.isEmpty())throw new AssertionError("idle heartbeat lifted chart hold");
        client.close();warm.close();if(!client.active.isEmpty())throw new AssertionError("warm close leaves input");
        RootWarmup abandoned=new RootWarmup(new Context(),1000,600);RootClient unused=RootClient.last;abandoned.close();
        if(!unused.closed)throw new AssertionError("unused prepared channel not closed");
        System.out.println("PASS Root warm channel heartbeat, double-tap release, exclusive chart handoff, continuous hold and abandoned channel cleanup (host transport)");
    }
}
