package io.github.phiscript.capture;
import io.github.phiscript.input.TouchService;
public final class PhiraClientNotice {
    public static int count;
    public static void show(TouchService service,String message){
        if(!message.contains("重新开始会话"))throw new AssertionError("missing restart guidance");
        count++;
    }
}
