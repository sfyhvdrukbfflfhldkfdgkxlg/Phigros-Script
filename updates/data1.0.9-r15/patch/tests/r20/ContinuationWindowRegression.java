package io.github.phiscript.input;

import android.accessibilityservice.GestureDescription;

/** A one-tick UP delays the next DOWN. Safety must see the duration Android will use. */
public final class ContinuationWindowRegression {
    public static void main(String[] args) {
        GestureBatch<Integer> batch = new GestureBatch<>();
        batch.add(1,null,GestureChannel.path(100,100,110,100),0,1,true,110,100);
        batch.add(2,null,GestureChannel.path(200,100,200,100),0,1,false,200,100);
        batch.add(3,null,GestureChannel.path(300,100,300,100),1,2,false,300,100);
        long scheduledEnd = batch.normalizedEndOffset();
        long[] keptEnd = {-1};
        batch.rejectUnsafe((key,prior,down,up,keep)->{
            if(keep)keptEnd[0]=up;
            return true;
        });
        GestureDescription sent = batch.build();
        if (scheduledEnd != 3 || keptEnd[0] != scheduledEnd
                || sent.strokes.get(0).duration != scheduledEnd || batch.duration != scheduledEnd)
            throw new AssertionError("Continuation was checked before resolving the DOWN/UP collision");
        System.out.println("PASS continued contact certified through the normalized batch end");
    }
}
