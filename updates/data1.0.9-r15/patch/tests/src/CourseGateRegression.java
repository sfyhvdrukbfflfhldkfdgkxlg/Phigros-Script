package io.github.phiscript.capture;

public final class CourseGateRegression {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static PhigrosCourseGate.Decision frame(PhigrosCourseGate gate,long at,boolean hud,String id){return gate.observe(at,at,hud,false,false,id);}
    public static void main(String[] args){
        PhigrosCourseGate gate=new PhigrosCourseGate();
        ok(frame(gate,1000,true,"same")!=PhigrosCourseGate.Decision.READY,"joined a song already in progress");
        ok(frame(gate,1200,true,"same")!=PhigrosCourseGate.Decision.READY,"HUD alone started a course");
        frame(gate,1400,false,null);
        ok(frame(gate,1400,false,null)==PhigrosCourseGate.Decision.WAIT,"duplicate boundary frame");
        frame(gate,1600,false,null);frame(gate,1800,true,"same");
        ok(frame(gate,1800,true,"same")==PhigrosCourseGate.Decision.WAIT,"duplicate identity frame");
        ok(gate.observe(1950,3000,true,false,false,"same")==PhigrosCourseGate.Decision.WAIT,"stale identity accepted");
        ok(frame(gate,2000,true,"same")==PhigrosCourseGate.Decision.READY,"fresh first song did not start");
        ok(gate.firstHud()==1800&&gate.stage()==1,"first HUD origin lost");
        gate.playing();gate.finished(false);
        ok(frame(gate,2200,true,"same")==PhigrosCourseGate.Decision.WAIT,"last song immediately replayed");
        frame(gate,2400,false,null);frame(gate,2600,false,null);frame(gate,2800,true,"same");
        ok(frame(gate,3000,true,"same")==PhigrosCourseGate.Decision.READY,"same song in second slot not supported");
        ok(gate.stage()==2&&gate.firstHud()==2800,"second song reused first origin");
        gate.playing();gate.finished(true);frame(gate,3200,true,"third");
        ok(frame(gate,3400,true,"third")==PhigrosCourseGate.Decision.READY,"verified playback transition lost");
        gate.playing();gate.finished(false);
        ok(gate.complete()&&frame(gate,3600,true,"fourth")==PhigrosCourseGate.Decision.COMPLETE,"played beyond three songs");
        PhigrosCourseGate paused=new PhigrosCourseGate();
        ok(paused.observe(10,10,false,true,false,null)==PhigrosCourseGate.Decision.PAUSED,"pause treated as transition");
        ok(paused.observe(20,20,false,false,true,null)==PhigrosCourseGate.Decision.RESULT,"result treated as transition");
        PhigrosCourseGate ambiguous=new PhigrosCourseGate();frame(ambiguous,100,false,null);frame(ambiguous,300,false,null);frame(ambiguous,500,true,"a");
        ok(frame(ambiguous,700,true,"b")==PhigrosCourseGate.Decision.WAIT,"conflicting identity started input");
        ok(frame(ambiguous,900,true,null)==PhigrosCourseGate.Decision.WAIT,"missing identity started input");
        frame(ambiguous,1100,true,"b");ok(frame(ambiguous,1300,true,"b")==PhigrosCourseGate.Decision.READY,"identity did not recover");
        ambiguous.playing();ambiguous.finished(false);
        ok(frame(ambiguous,1500,true,"b")==PhigrosCourseGate.Decision.WAIT,"unchanged HUD replayed old song");
        ok(frame(ambiguous,1700,true,"c")==PhigrosCourseGate.Decision.WAIT,"single changed title counted as transition");
        ok(frame(ambiguous,1900,true,"c")==PhigrosCourseGate.Decision.READY,"continuous-HUD course did not advance");
        ok(ambiguous.firstHud()==1700,"continuous-HUD origin reused previous song");
        ambiguous.playing();ambiguous.finished(false);frame(ambiguous,2100,true,"d");
        ok(frame(ambiguous,2300,true,"d")==PhigrosCourseGate.Decision.READY,"third song without HUD gap blocked");
        ambiguous.playing();ambiguous.finished(false);ok(ambiguous.complete(),"continuous-HUD course did not stop at three");
        System.out.println("PASS "+checks+" course boundary, fresh evidence, repeated-song and stop checks.");
    }
}
