package io.github.phiscript.capture;

import io.github.phiscript.input.TouchService;

/** Bind one session worker to its first Phira client, retaining exact input focus guards. */
public final class PhiraForeground {
    private static final ThreadLocal<String> client=new ThreadLocal<>();
    private PhiraForeground(){}
    public static void beginSession(){client.remove();}
    public static boolean isForeground(){
        TouchService service=TouchService.Companion.getCurrent();
        if(service==null)return false;
        String foreground=service.getForegroundPackage();
        if(!PhiraPackages.contains(foreground))return false;
        String selected=client.get();
        if(selected!=null&&!selected.equals(foreground)){
            String message="Phira 客户端已更换，本次打谱已停止；请重新开始会话后再确认谱面";
            PhiraClientNotice.show(service,message);
            throw new IllegalStateException(message);
        }
        try{
            // selectGame refuses changes while a menu gesture or chart is playing.
            // Switching between clients during playback therefore still stops input.
            if(!foreground.equals(service.getTargetPackage()))service.selectGame(foreground);
            if(!service.getGameForeground())return false;
            client.set(foreground);
            return true;
        }catch(IllegalStateException busy){return false;}
    }
}
