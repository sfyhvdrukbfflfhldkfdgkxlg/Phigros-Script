package io.github.phiscript.capture;
public final class AutoStateRegression {
    static int checks;
    static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        AutoEvidence e=new AutoEvidence();ok(!e.observe("song/IN",1000,1000),"single frame");
        for(int i=0;i<20;i++)ok(!e.observe("song/IN",1000,1200),"reused frame voted");
        ok(!e.observe("song/IN",1100,1100),"evidence too short");ok(e.observe("song/IN",1200,1200),"two fresh frames");
        ok(!e.observe("other/AT",1300,1300),"identity changed");ok(!e.observe(null,1500,1500),"ambiguous OCR");
        ok(!e.observe("song/IN",1600,2601),"stale OCR");ok(!e.observe("song/IN",2602,2601),"future capture");
        e.reset();ok(!e.observe("a",1000,1000),"reset");ok(!e.observe("a",4000,4000),"long evidence gap");
        PhigrosStartGate gate=new PhigrosStartGate();ok(gate.observe(1000,1000,true,false,true)<0,"not armed");
        gate.command(45000);ok(gate.observe(45000,45000,true,false,true)<0,"pre-retry frame");
        ok(gate.observe(45010,45010,false,true,true)<0,"pause menu");
        ok(gate.observe(45200,45200,true,false,false)<0,"first HUD");ok(gate.observe(45240,45240,true,false,false)<0,"no restart receipt");
        ok(gate.observe(45240,45250,true,false,true)<0,"duplicate HUD");
        ok(gate.observe(45600,45600,true,false,true)==45200,"clock must preserve first HUD, not OCR completion");
        ok(gate.observe(45640,45640,true,false,true)<0,"clock locked twice");
        gate.command(50000);ok(gate.observe(50100,50400,true,false,true)<0,"stale HUD");
        ok(gate.observe(50410,50410,true,false,false)<0,"new fresh HUD");ok(gate.observe(50450,50450,true,false,false)<0,"pending validation");
        ok(gate.observe(51400,51400,true,false,true)<0,"late validation cannot invent a new origin");
        ok(gate.observe(51440,51440,true,false,true)<0,"failed origin cannot revive");
        gate.command(60000);gate.observe(60100,60100,true,false,false);gate.observe(60140,60140,true,false,false);
        ok(gate.observe(60180,60180,false,true,true)<0,"menu returned");ok(gate.observe(60220,60220,true,false,true)<0,"menu interruption cannot restart same origin");
        ok(PhigrosStartGate.epoch(10000,30,500,-10)==10460,"calibration arithmetic");
        for(int offset:new int[]{-3001,10001}){boolean rejected=false;try{PhigrosStartGate.epoch(1,0,offset,0);}catch(IllegalArgumentException ex){rejected=true;}ok(rejected,"invalid offset");}
        System.out.println("PASS "+checks+" automatic identity/start-clock checks: fresh/distinct votes, ambiguity, stale/future frames, restart boundary, restart-receipt validation, bounded verification delay, menu interruption and calibration");
    }
}
