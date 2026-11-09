package io.github.phiscript.capture;

import io.github.phiscript.input.TouchService;
import io.github.phiscript.root.PhiraExport;
import java.io.File;
import java.util.List;

/** Exercise idle client selection and an exact client boundary during active input. */
public final class PhiraProRegression {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        ok(!PhiraForeground.isForeground(),"missing service accepted");
        TouchService service=new TouchService();TouchService.current=service;
        for(String pkg:PhiraPackages.all()){
            PhiraForeground.beginSession();
            service.foreground(pkg);ok(PhiraForeground.isForeground(),"known idle client rejected: "+pkg);
            ok(pkg.equals(service.target),"wrong exact input target: "+pkg);
            service.playing=true;ok(PhiraForeground.isForeground(),"active selected client rejected");
            int selections=service.selections;
            service.foreground(pkg.equals(PhiraPackages.PRO)?PhiraPackages.OFFICIAL:PhiraPackages.PRO);
            boolean stopped=false;try{PhiraForeground.isForeground();}catch(IllegalStateException expected){stopped=true;}
            ok(stopped,"cross-client switch allowed while playing");
            ok(service.cancelled&&pkg.equals(service.target)&&selections==service.selections,"active target was rebound instead of cancelled");
            service.playing=false;service.cancelled=false;
            stopped=false;try{PhiraForeground.isForeground();}catch(IllegalStateException expected){stopped=true;}
            ok(stopped&&pkg.equals(service.target),"confirmation/retry idle gap rebound old selection to another client");
            PhiraForeground.beginSession();ok(PhiraForeground.isForeground(),"new run did not clear client lock");
        }
        for(String pkg:new String[]{null,"","com.android.systemui","org.flos.phirapro.fake","evil.org.flos.phira","com.PigeonGames.Phigros"}){
            service.foreground(pkg);ok(!PhiraForeground.isForeground(),"unrelated foreground accepted: "+pkg);
        }
        String[] copy=PhiraPackages.all();copy[0]="evil.app";ok(PhiraPackages.all()[0].equals(PhiraPackages.OFFICIAL),"package list was mutable");
        ok(PhiraClientNotice.count==4,"cross-client stop did not explain how to restart");
        List<File> roots=PhiraExport.roots(10);
        for(String pkg:PhiraPackages.all()){
            ok(roots.contains(new File("/data/user/10/"+pkg+"/files/data/charts")),"private chart root missing: "+pkg);
            ok(roots.contains(new File("/storage/emulated/10/Android/data/"+pkg+"/files/data/charts")),"external chart root missing: "+pkg);
        }
        for(File root:roots)ok(root.getPath().contains("/10/")&&(root.getPath().contains("/org.flos.phira/")||root.getPath().contains("/org.flos.phirapro/")),"scan escaped selected user/packages");
        System.out.println("PASS "+checks+" Phira/Pro foreground, exact active-client stop and Root chart-path checks (host model, no Android device).");
    }
}
