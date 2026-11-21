package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.engine.*;
import java.util.Arrays;
import java.util.Collections;
import org.json.*;

public final class HoldOscillationRegression {
    private static final Viewport VIEW = new Viewport(0, 0, 1600, 900);
    private static final int START = 200, N = 501;
    private static final float DOWN_Y = 240;

    private static void ok(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Note note(int type) {
        return new Note(1, type, START/1000.0, (START+N-1)/1000.0, .4, 1, 0, true, 0);
    }
    private static float wave(int i) {
        int phase=Math.floorMod(i, 100);
        return DOWN_Y + Math.min(phase, 100-phase)*5;
    }
    private static Chart chart(Note note, boolean shaking) {
        return Chart.Companion.fromNative$app_debug(Collections.singletonList(note), 0,
                (n, t, v) -> new Point(400, shaking ? wave((int)Math.round(t*1000)-START) : DOWN_Y),
                (n, t, v) -> new Point(0, 1));
    }
    private static final class Samples {
        final float[] x = new float[N], y = new float[N];
        final byte[] link = new byte[N];
        Samples() {
            Arrays.fill(x, 403);
            Arrays.fill(link, (byte)1);
            link[0]=0;
            for (int i=0; i<N; i++) y[i]=wave(i)+2;
        }
        boolean apply(Chart chart, Note note, boolean flick, boolean local) {
            return HoldOscillation.stabilize(chart, VIEW, new TouchEvent(note, START, START+N-1),
                    flick, x, y, link, START+6, null, local);
        }
    }
    private static JSONObject xy(double x, double y) throws Exception { return new JSONObject().put("x",x).put("y",y); }

    public static void main(String[] args) throws Exception {
        TapMod.init(new Context());
        Note hold=note(3);
        Chart shaking=chart(hold,true), steady=chart(hold,false);
        for(boolean local:new boolean[]{false,true}) {
            Samples track=new Samples();
            ok(track.apply(shaking,hold,false,local),"Large sustained shaking was missed");
            for(int i=0; i<N; i++) ok(track.x[i]==403&&track.y[i]==DOWN_Y+2,"Did not preserve actual randomized DOWN at "+i);
            ok(!new Samples().apply(shaking,hold,true,local),"Swallowed a required red gesture");
            ok(!new Samples().apply(shaking,note(2),false,local),"Swallowed a drag gesture");
            Samples monotone=new Samples(), one=new Samples(), small=new Samples();
            for(int i=0;i<N;i++) {
                monotone.y[i]=220+i;
                one.y[i]=350+Math.min(i, N-1-i);
                small.y[i]=300+(wave(i)-DOWN_Y)/5;
            }
            ok(!monotone.apply(shaking,hold,false,local),"Locked a smooth pan");
            ok(!one.apply(shaking,hold,false,local),"Locked a single wobble");
            ok(!small.apply(shaking,hold,false,local),"Locked a small oscillation");
        }
        ok(!new Samples().apply(steady,hold,false,true),"Mistook a routed detour for raw line motion");
        Samples broken=new Samples();
        broken.link[250]=0;
        float secondDown=broken.y[250];
        broken.apply(shaking,hold,false,false);
        ok(broken.link[250]==0&&broken.y[300]==secondDown,"Lock crossed separate contact heads");

        Chart blocked=chart(hold,true);
        JSONObject area=new JSONObject().put("bottomLeftPercentage",xy(.23,.70)).put("topRightPercentage",xy(.27,.76))
                .put("enableTime",.43).put("disableTime",.46);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));
        BlockAreaSet.bind(blocked);
        Samples guarded=new Samples();
        guarded.apply(blocked,hold,false,true);
        boolean released=false;
        for(int i=1;i<N;i++) {
            if(guarded.link[i]==0) released=true;
            Point q=new Point(guarded.x[i],guarded.y[i]);
            ok(TapMod.clearAt(blocked,VIEW,q,(START+6+i)/1000.0),"Frozen point violated a moving noise window");
        }
        ok(released,"Did not break the hold when its anchor became obstructed");
        System.out.println("PASS sustained vertical HOLD lock: local and cloud, preserved DOWN, flick/drag, ordinary movement, contact breaks and noise clearance");
    }
}
