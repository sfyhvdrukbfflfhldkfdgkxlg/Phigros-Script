package io.github.phiscript.root;
import io.github.phiscript.AppSettings;
import io.github.phiscript.PlayMode;
/** One mode decision shared by source selection, startup checks, labels and capture dispatch. */
public final class ModeRouting {
    public enum Kind{PHIGROS_AUTO,PHIGROS_MANUAL,VISUAL,PHIRA}
    private static final String AUTO="phigros_auto_mode",MANUAL="phigros_manual_mode",SCHEMA="mode_routing_schema";
    private ModeRouting(){}
    public static Kind kind(AppSettings p){
        if("PHIRA".equals(p.text("mode","VISUAL")))return Kind.PHIRA;
        if(p.flag(MANUAL,false))return Kind.PHIGROS_MANUAL;
        if(p.flag(AUTO,false))return Kind.PHIGROS_AUTO;
        return Kind.VISUAL;
    }
    public static boolean needsPhigros(AppSettings p){Kind k=kind(p);return k==Kind.PHIGROS_AUTO||k==Kind.PHIGROS_MANUAL;}
    public static boolean needsPhira(AppSettings p){return kind(p)==Kind.PHIRA;}
    public static boolean autoEngine(AppSettings p,PlayMode capturedMode){return capturedMode==PlayMode.VISUAL&&needsPhigros(p);}
    private static void record(AppSettings p){p.save(SCHEMA,"4");p.save("play_route",kind(p).name());}
    public static void select(AppSettings p,int index){
        if(index<0||index>3)throw new IllegalArgumentException("演奏模式无效");
        p.save("mode",index==1?"PHIRA":"VISUAL");p.saveFlag(AUTO,index==0);p.saveFlag(MANUAL,index==3);record(p);
    }
    /** Called after the real preference write, including writes from the old Phira import UI. */
    public static void onSaved(AppSettings p,String key,String value){
        if(!"mode".equals(key))return;
        if("PHIRA".equals(value)||"VISUAL".equals(value)){p.saveFlag(AUTO,false);p.saveFlag(MANUAL,false);record(p);}
    }
    public static void onFlagSaved(AppSettings p,String key,boolean value){
        if(!AUTO.equals(key)&&!MANUAL.equals(key))return;
        if("PHIRA".equals(p.text("mode","VISUAL"))&&value){p.saveFlag(key,false);return;}
        if(value){String other=AUTO.equals(key)?MANUAL:AUTO;if(p.flag(other,false))p.saveFlag(other,false);}
        record(p);
    }
    public static void migrateLegacy(AppSettings p){
        if("4".equals(p.text(SCHEMA,"")))return;
        Kind previous=kind(p);
        // r2/r3's explicit Phigros-local import could leave mode=PHIRA behind.
        if("local".equals(p.text("phigros_library_source",""))&&previous==Kind.PHIRA)previous=Kind.PHIGROS_AUTO;
        select(p,previous==Kind.PHIRA?1:previous==Kind.VISUAL?2:previous==Kind.PHIGROS_MANUAL?3:0);
    }
    public static String label(AppSettings p){switch(kind(p)){case PHIRA:return "org.flos.phirapro".equals(p.text("phira_package",""))?"Phira Pro 谱面演奏":"Phira 谱面演奏";case PHIGROS_MANUAL:return "Phigros 手选谱面";case PHIGROS_AUTO:return "Phigros 自动读谱";default:return "Phigros 纯视觉";}}
}
