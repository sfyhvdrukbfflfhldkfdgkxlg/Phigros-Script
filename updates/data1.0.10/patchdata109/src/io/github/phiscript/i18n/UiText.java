package io.github.phiscript.i18n;

import android.content.Context;
import android.content.res.Configuration;
import io.github.phiscript.AppSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Localizes display strings only. Chart data and OCR matching tokens stay unchanged. */
public final class UiText {
    private static volatile String language="auto";
    private static final Map<String,String> EN=EnglishCatalog.entries();
    private static final List<String> KEYS=new ArrayList<>(EN.keySet());
    static {KEYS.sort((a,b)->Integer.compare(b.length(),a.length()));}
    private UiText(){}
    public static void attach(Context context){
        language=new AppSettings(context).text("ui_language","auto");
        if(!language.equals("auto")&&!language.equals("en")&&!language.equals("zh"))language="auto";
        {
            Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocale(language.equals("auto")?Locale.getDefault():english()?Locale.ENGLISH:Locale.SIMPLIFIED_CHINESE);
            context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
        }
    }
    public static boolean english(){return language.equals("en")||(language.equals("auto")&&Locale.getDefault().getLanguage().equals("en"));}
    public static void selectForTest(String choice){language=choice;}
    public static String text(String source){return !english()||source==null?source:translate(source,0);}
    public static CharSequence text(CharSequence source){if(source==null)return null;String translated=text(source.toString());return translated.equals(source.toString())?source:translated;}
    public static int text(int resource){return resource;}
    public static String[] text(String[] source){String[] out=source.clone();for(int i=0;i<out.length;i++)out[i]=text(out[i]);return out;}
    public static CharSequence[] text(CharSequence[] source){CharSequence[] out=source.clone();for(int i=0;i<out.length;i++)out[i]=text(out[i]);return out;}
    private static String translate(String source,int depth){
        if(depth>12||source.isEmpty())return source;String exact=EN.get(source);if(exact!=null)return exact;
        if(source.indexOf('\n')>=0){String[] rows=source.split("\n",-1);for(int i=0;i<rows.length;i++)rows[i]=translate(rows[i],depth+1);return String.join("\n",rows);}
        if(source.contains("  ·  ")){String[] parts=source.split("  ·  ",-1);for(int j=0;j<parts.length;j++)parts[j]=translate(parts[j],depth+1);return String.join("  ·  ",parts);}
        for(String label:new String[]{"Phigros  ","Phira  "})if(source.startsWith(label))return label+translate(source.substring(label.length()),depth+1);
        int digits=0;while(digits<source.length()&&Character.isDigit(source.charAt(digits)))digits++;
        if(digits>0&&source.substring(digits).startsWith(" 个"))return source.substring(0,digits)+translate(source.substring(digits),depth+1);
        if(source.startsWith("[")){int close=source.indexOf("] ");if(close>0&&close<30)return source.substring(0,close+2)+translate(source.substring(close+2),depth+1);}
        for(String key:KEYS)if(key.length()>=4&&source.startsWith(key))return EN.get(key)+translate(source.substring(key.length()),depth+1);
        for(String key:KEYS)if(key.length()>=3&&(Character.isWhitespace(key.charAt(0))||"，；（".indexOf(key.charAt(0))>=0)&&source.endsWith(key))return translate(source.substring(0,source.length()-key.length()),depth+1)+EN.get(key);
        return source;
    }
}
