package io.github.phiscript.root;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.PlayMode;
import java.nio.file.Files;

/** Uses the APK's real settings setters and retained smali hooks. */
public final class CourseRoutingRegression {
    private static int checks;
    private static void ok(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Context.root=Files.createTempDirectory("r16-routing").toFile();
        Context context=new Context();AppSettings prefs=new AppSettings(context);
        ModeRouting.select(prefs,5);
        ok(ModeRouting.course(prefs),"course entry did not select Phigros");
        ok(prefs.mode()==PlayMode.VISUAL&&ModeRouting.autoEngine(prefs,PlayMode.VISUAL),"capture did not dispatch Phigros course");
        ok(!ModeRouting.autoEngine(prefs,PlayMode.PHIRA),"Phira capture incorrectly dispatched course");
        AppSettings restarted=new AppSettings(context);ModeRouting.migrateLegacy(restarted);
        ok(ModeRouting.course(restarted),"restart migration lost course selection");
        for(int index:new int[]{0,1,2,3}){
            ModeRouting.select(prefs,index);
            ok(!ModeRouting.course(prefs),"course flag leaked into mode "+index);
            ModeRouting.select(prefs,5);
        }
        prefs.save("mode","PHIRA");
        ok(!ModeRouting.course(prefs)&&ModeRouting.needsPhira(prefs),"original Phira setter did not clear course");
        ModeRouting.select(prefs,5);prefs.saveFlag("phigros_manual_mode",true);
        ok(!ModeRouting.course(prefs)&&ModeRouting.kind(prefs)==ModeRouting.Kind.PHIGROS_MANUAL,"manual import retained course");
        System.out.println("PASS "+checks+" final-APK course routing checks with real AppSettings persistence hooks.");
    }
}
