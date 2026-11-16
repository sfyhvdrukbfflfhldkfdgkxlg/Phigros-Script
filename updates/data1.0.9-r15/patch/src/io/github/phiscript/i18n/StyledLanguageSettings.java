package io.github.phiscript.i18n;
import android.app.Activity;
import android.app.AlertDialog;
import io.github.phiscript.AppSettings;
import io.github.phiscript.root.RootPanel;
import io.github.phiscript.root.UiDesign;
public final class StyledLanguageSettings {
    private StyledLanguageSettings(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        String[] values={"auto","zh","en"};String choice=new AppSettings(activity).text("ui_language","auto");int selected=0;for(int i=0;i<values.length;i++)if(values[i].equals(choice))selected=i;
        UiDesign.show(UiDesign.dialog(activity,"语言 / Language").setSingleChoiceItems(UiText.text(new String[]{"跟随系统","简体中文","English"}),selected,(dialog,index)->{
            new AppSettings(activity).save("ui_language",values[index]);UiText.attach(activity);dialog.dismiss();activity.recreate();
        }).setNegativeButton(UiText.text("取消"),null).create());
    }
}
