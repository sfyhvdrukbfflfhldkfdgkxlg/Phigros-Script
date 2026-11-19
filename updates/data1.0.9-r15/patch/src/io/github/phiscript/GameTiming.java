package io.github.phiscript;

public final class GameTiming {
    public static final int PHIRA_DELAY_MS = 715;
    public static final int PHIGROS_DELAY_MS = 1700;
    public static final String INCLUDE_PHIRA_CHART_OFFSET = "phira_include_chart_offset";

    private GameTiming() {}
    public static int defaultDelay(PlayMode mode) {
        return mode == PlayMode.PHIRA ? PHIRA_DELAY_MS : PHIGROS_DELAY_MS;
    }
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
        if ("phiraRestartDelay".equals(key))
            fallback = Integer.toString(phigros ? PHIGROS_DELAY_MS : PHIRA_DELAY_MS);
        return prefs.text(key(phigros,key),fallback);
    }
    public static String snapshotText(AppSettings prefs,String key,String fallback){
        return read(prefs,!"PHIRA".equals(prefs.text("mode","VISUAL")),key,fallback);
    }
}
