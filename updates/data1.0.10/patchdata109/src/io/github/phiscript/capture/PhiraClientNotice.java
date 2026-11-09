package io.github.phiscript.capture;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import io.github.phiscript.input.TouchService;

/** Visible explanation when an already selected client changes before or during playback. */
public final class PhiraClientNotice {
    private PhiraClientNotice(){}
    public static void show(TouchService service,String message){
        try{new Handler(Looper.getMainLooper()).post(()->Toast.makeText(service,io.github.phiscript.i18n.UiText.text(message),Toast.LENGTH_LONG).show());}
        catch(RuntimeException ignored){}
    }
}
