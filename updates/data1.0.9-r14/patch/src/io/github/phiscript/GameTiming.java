package io.github.phiscript;

public final class GameTiming {
    private GameTiming() {}
    public static String key(boolean phigros,String key) {
        if(!phigros)return key;
        switch(key){
            case "phiraRestartDelay":return "phigrosRestartDelay";
            case "phiraGlobalOffset":return "phigrosGlobalOffset";
            case "touchOffset":return "phigrosTouchOffset";
            default:return key;
        }
    }
    public static String read(AppSettings prefs,boolean phigros,String key,String fallback){
        return prefs.text(key(phigros,key),phigros&&"phiraRestartDelay".equals(key)?"0":fallback);
    }
    public static String snapshotText(AppSettings prefs,String key,String fallback){
        return read(prefs,!"PHIRA".equals(prefs.text("mode","VISUAL")),key,fallback);
    }
}
