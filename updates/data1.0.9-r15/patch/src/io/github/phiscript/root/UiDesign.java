package io.github.phiscript.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import io.github.phiscript.i18n.UiText;

/** Native design primitives shared by home, libraries and settings dialogs. */
public final class UiDesign {
    private static final int STYLED_TEXT=0x7f0f7b04;
    public static final int BG=0xfff5f7f4,SURFACE=0xffffffff,RAISED=0xffedf1ed,
        INK=0xff162d38,MUTED=0xff304852,ACCENT=0xff145e5a,DANGER=0xff8f3145,LINE=0xffccdae0,
        MINT=0xffd9f0eb,ON_ACCENT=0xffffffff;
    public static final int PLAY=0,LIBRARY=1,DOWNLOAD=2,IMPORT=3,TRASH=4,SETTINGS=5,SHIELD=6,VIDEO=7,HISTORY=8;
    private UiDesign(){}
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static String words(String chinese,String english){return UiText.english()?english:UiText.text(chinese);}
    public static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static RippleDrawable ripple(Context c,int color,int radius){
        ColorStateList tint=ColorStateList.valueOf(color==ACCENT||color==DANGER?0x33ffffff:0x24216950);
        Drawable mask=shape(c,0xffffffff,radius);
        if(color==ACCENT || color==DANGER)
            return new RippleDrawable(tint,GlassSurface.command(c,color==DANGER),mask);
        if(color==MINT||color==RAISED){
            GlassSurface glass=new GlassSurface(c,radius,color==MINT);
            return new RippleDrawable(tint,glass,mask){
                @Override public void setHotspot(float x,float y){super.setHotspot(x,y);glass.setHotspot(x,y);}
            };
        }
        return new RippleDrawable(tint,shape(c,color,radius),mask);
    }
    public static LinearLayout.LayoutParams space(Context c,int width,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.topMargin=dp(c,top);return p;}
    public static TextView text(Context c,String value,int size,int color,boolean bold){
        TextView v=new TextView(c);v.setText(UiText.text(value));v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(c,3),1);v.setIncludeFontPadding(false);v.setTag(STYLED_TEXT,true);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    public static LinearLayout card(Context c){LinearLayout box=new LinearLayout(c);box.setOrientation(1);box.setBackground(new GlassSurface(c,24,false));box.setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));box.setElevation(dp(c,2));return box;}
    public static Button button(Context c,String title,boolean primary,Runnable click){
        Button b=new Button(c);b.setText(UiText.text(title));b.setAllCaps(false);b.setTextSize(primary?17:14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?ON_ACCENT:INK);b.setStateListAnimator(null);
        b.setMinHeight(dp(c,primary?56:48));b.setMinimumHeight(dp(c,primary?56:48));b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setBackground(ripple(c,primary?ACCENT:RAISED,14));
        b.setContentDescription(UiText.text(title));b.setOnClickListener(v->click.run());UiMotion.bindPress(b);return b;
    }
    public static TextView icon(Context c,int kind){TextView v=new TextView(c);v.setBackground(shape(c,RAISED,12));v.setCompoundDrawablesWithIntrinsicBounds(new Mark(c,kind),null,null,null);v.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    public static LinearLayout row(Context c,int kind,String title,String subtitle,Runnable click){
        TextView detail=TextUtils.isEmpty(subtitle)?null:text(c,subtitle,12,MUTED,false);
        return rowWithDetail(c,kind,title,detail,click);
    }
    static LinearLayout rowWithDetail(Context c,int kind,String title,TextView detail,Runnable click){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,10),dp(c,12),dp(c,10),dp(c,12));row.setMinimumHeight(dp(c,68));row.setBackground(ripple(c,0x55ffffff,14));
        row.addView(icon(c,kind),new LinearLayout.LayoutParams(dp(c,42),dp(c,42)));
        LinearLayout copy=new LinearLayout(c);copy.setOrientation(1);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMarginStart(dp(c,12));row.addView(copy,cp);
        copy.addView(text(c,title,15,INK,true));if(detail!=null)copy.addView(detail,space(c,-1,-2,5));
        TextView arrow=text(c,"›",24,MUTED,false);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(c,24),-2));
        row.setFocusable(true);row.setClickable(true);row.setContentDescription(UiText.text(title)+(detail==null?"":", "+detail.getText()));
        copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setOnClickListener(v->click.run());UiMotion.bindPress(row);return row;
    }
    public static AlertDialog.Builder dialog(Activity a,String title){
        // Background blur requires windowIsTranslucent before Android creates the dialog window.
        Context base=android.os.Build.VERSION.SDK_INT>=31
                ?new android.view.ContextThemeWrapper(a,android.R.style.Theme_Translucent):a;
        return new AlertDialog.Builder(base,android.R.style.Theme_Material_Light_Dialog_Alert).setTitle(UiText.text(title));
    }
    public static AlertDialog show(AlertDialog dialog){
        android.view.Window window=dialog.getWindow();
        if(window!=null){
            int state=window.getAttributes().softInputMode & ~android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
            window.setSoftInputMode(state|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            GlassWindows.dialog(window);
        }
        UiMotion.show(dialog);
        if(window!=null){
            if(android.os.Build.VERSION.SDK_INT>=29)window.getDecorView().setForceDarkAllowed(false);
        }
        tint(dialog.getWindow()==null?null:dialog.getWindow().getDecorView());
        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            Button b=dialog.getButton(id);if(b!=null){b.setTextColor(ACCENT);b.setAllCaps(false);b.setMinHeight(dp(b.getContext(),48));UiMotion.bindPress(b);}
        }
        ListView list=dialog.getListView();if(list!=null){list.setDividerHeight(dp(list.getContext(),1));list.setDivider(new android.graphics.drawable.ColorDrawable(LINE));list.setSelector(ripple(list.getContext(),SURFACE,8));list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){tint(child);}public void onChildViewRemoved(View parent,View child){}});}
        return dialog;
    }
    public static AlertDialog showNative(AlertDialog.Builder builder){
        AlertDialog dialog=builder.create();
        if(dialog.getWindow()!=null)GlassWindows.dialog(dialog.getWindow());
        return UiMotion.show(dialog);
    }
    private static void tint(View view){
        if(view==null)return;
        if(view instanceof TextView){TextView t=(TextView)view;if(!Boolean.TRUE.equals(t.getTag(STYLED_TEXT)))t.setTextColor(INK);if(view instanceof EditText){t.setHintTextColor(MUTED);t.setBackgroundTintList(ColorStateList.valueOf(ACCENT));}else if(t.getId()==android.R.id.text1)t.setMinHeight(dp(view.getContext(),56));}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)tint(group.getChildAt(i));}
    }
    public static LinearLayout inputBox(Activity a,String description,EditText field){
        LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(dp(a,24),dp(a,10),dp(a,24),dp(a,10));
        if(description!=null&&!description.isEmpty())box.addView(text(a,description,13,MUTED,false),space(a,-1,-2,0));
        field.setTextColor(INK);field.setHintTextColor(MUTED);field.setMinHeight(dp(a,52));field.setBackgroundTintList(ColorStateList.valueOf(ACCENT));box.addView(field,space(a,-1,-2,12));return box;
    }
    static Drawable mark(Context c,int kind){return new Mark(c,kind);}
    private static final class Mark extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path q=new Path();final int size,kind;
        Mark(Context c,int kind){size=dp(c,22);this.kind=kind;p.setColor(ACCENT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);setBounds(0,0,size,size);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(size/24f,size/24f);q.reset();
            if(kind==PLAY){q.moveTo(8,5);q.lineTo(19,12);q.lineTo(8,19);q.close();canvas.drawPath(q,p);}
            else if(kind==DOWNLOAD||kind==IMPORT){canvas.drawLine(12,3,12,15,p);q.moveTo(7,10);q.lineTo(12,15);q.lineTo(17,10);canvas.drawPath(q,p);canvas.drawLine(5,20,19,20,p);if(kind==IMPORT){canvas.drawLine(4,17,4,20,p);canvas.drawLine(20,17,20,20,p);}}
            else if(kind==TRASH){canvas.drawLine(5,6,19,6,p);canvas.drawRoundRect(7,6,17,21,2,2,p);canvas.drawLine(10,3,14,3,p);canvas.drawLine(10,10,10,17,p);canvas.drawLine(14,10,14,17,p);}
            else if(kind==VIDEO){canvas.drawRoundRect(3,6,16,18,3,3,p);q.moveTo(16,10);q.lineTo(21,7);q.lineTo(21,17);q.lineTo(16,14);canvas.drawPath(q,p);}
            else if(kind==SHIELD){q.moveTo(12,3);q.lineTo(20,6);q.lineTo(19,14);q.quadTo(17,19,12,22);q.quadTo(7,19,5,14);q.lineTo(4,6);q.close();canvas.drawPath(q,p);q.reset();q.moveTo(8,12);q.lineTo(11,15);q.lineTo(16,9);canvas.drawPath(q,p);}
            else if(kind==SETTINGS){canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2.5f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}}
            else if(kind==HISTORY){canvas.drawCircle(12,12,9,p);canvas.drawLine(12,6,12,12,p);canvas.drawLine(12,12,16,14,p);}
            else{canvas.drawRoundRect(4,5,20,20,3,3,p);canvas.drawLine(8,3,8,17,p);canvas.drawLine(8,10,16,10,p);canvas.drawLine(12,14,16,14,p);}canvas.restore();
        }
        public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
