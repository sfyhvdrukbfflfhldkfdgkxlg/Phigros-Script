package io.github.phiscript;
import android.content.Context;
import io.github.phiscript.root.ModeRouting;
import io.github.phiscript.root.PhiraChartRoots;
public final class TimingRegression {
    static void clock(AppSettings prefs,int restart,int global,int touch){
        SessionSettings s=prefs.snapshot();
        if(s.getPhiraRestartDelayMs()!=restart||s.getPhiraGlobalOffsetMs()!=global||s.getTouchOffsetMs()!=touch)
            throw new AssertionError("Wrong game clock "+s.getPhiraRestartDelayMs()+"/"+s.getPhiraGlobalOffsetMs()+"/"+s.getTouchOffsetMs());
    }
    public static void main(String[] args)throws Exception {
        AppSettings p=new AppSettings(new Context());
        ModeRouting.select(p,0);clock(p,0,0,0);
        ModeRouting.select(p,1);clock(p,700,0,0);
        p.save("phiraRestartDelay","820");p.save("phiraGlobalOffset","-31");p.save("touchOffset","27");
        clock(p,820,-31,27);ModeRouting.select(p,0);clock(p,0,0,0);
        p.save("phigrosRestartDelay","77");p.save("phigrosGlobalOffset","-42");p.save("phigrosTouchOffset","13");
        clock(p,77,-42,13);ModeRouting.select(p,3);clock(p,77,-42,13);ModeRouting.select(p,1);clock(p,820,-31,27);
        java.util.List<java.io.File> roots=PhiraChartRoots.roots(10);
        if(roots.size()!=24||roots.stream().filter(f->f.getPath().contains("/org.flos.phirapro/")).count()!=12)throw new AssertionError("Pro roots missing");
        System.out.println("PASS real AppSettings.snapshot: 7 game switches/default/migration checks, 24 profile-specific root chart directories.");
    }
}
