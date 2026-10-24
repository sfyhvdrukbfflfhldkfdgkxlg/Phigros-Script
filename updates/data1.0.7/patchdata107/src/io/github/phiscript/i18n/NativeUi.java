package io.github.phiscript.i18n;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/** Display-only bridges used by the existing Kotlin DEX. */
public final class NativeUi {
    private NativeUi(){}
    public static void text(TextView v,CharSequence s){v.setText(UiText.text(s));}
    public static void textRes(TextView v,int id){v.setText(UiText.text(v.getContext().getText(id)));}
    public static void textBuffer(TextView v,CharSequence s,TextView.BufferType type){v.setText(UiText.text(s),type);}
    public static void description(View v,CharSequence s){v.setContentDescription(UiText.text(s));}
    public static AlertDialog.Builder title(AlertDialog.Builder b,CharSequence s){return b.setTitle(UiText.text(s));}
    public static AlertDialog.Builder message(AlertDialog.Builder b,CharSequence s){return b.setMessage(UiText.text(s));}
    public static AlertDialog.Builder positive(AlertDialog.Builder b,CharSequence s,DialogInterface.OnClickListener l){return b.setPositiveButton(UiText.text(s),l);}
    public static AlertDialog.Builder negative(AlertDialog.Builder b,CharSequence s,DialogInterface.OnClickListener l){return b.setNegativeButton(UiText.text(s),l);}
    public static AlertDialog.Builder neutral(AlertDialog.Builder b,CharSequence s,DialogInterface.OnClickListener l){return b.setNeutralButton(UiText.text(s),l);}
    public static AlertDialog.Builder items(AlertDialog.Builder b,CharSequence[] s,DialogInterface.OnClickListener l){return b.setItems(UiText.text(s),l);}
    public static AlertDialog.Builder choices(AlertDialog.Builder b,CharSequence[] s,int selected,DialogInterface.OnClickListener l){return b.setSingleChoiceItems(UiText.text(s),selected,l);}
    public static Toast toast(Context c,CharSequence s,int duration){return Toast.makeText(c,UiText.text(s),duration);}
    public static Notification.Builder noticeTitle(Notification.Builder b,CharSequence s){return b.setContentTitle(UiText.text(s));}
    public static Notification.Builder noticeText(Notification.Builder b,CharSequence s){return b.setContentText(UiText.text(s));}
}
