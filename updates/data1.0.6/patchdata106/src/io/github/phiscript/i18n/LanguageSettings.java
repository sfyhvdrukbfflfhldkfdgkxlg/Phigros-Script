package io.github.phiscript.i18n;
import android.app.Activity;
import android.app.AlertDialog;
import io.github.phiscript.AppSettings;
import io.github.phiscript.root.RootPanel;
public final class LanguageSettings {
    private LanguageSettings(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        String[] values={"auto","zh","en"};String choice=new AppSettings(activity).text("ui_language","auto");int selected=0;for(int i=0;i<values.length;i++)if(values[i].equals(choice))selected=i;
        new AlertDialog.Builder(activity).setTitle(UiText.text("语言 / Language")).setSingleChoiceItems(UiText.text(new String[]{"跟随系统","简体中文","English"}),selected,(dialog,index)->{
            new AppSettings(activity).save("ui_language",values[index]);UiText.attach(activity);dialog.dismiss();activity.recreate();
        }).setNegativeButton(UiText.text("取消"),null).show();
    }
}
