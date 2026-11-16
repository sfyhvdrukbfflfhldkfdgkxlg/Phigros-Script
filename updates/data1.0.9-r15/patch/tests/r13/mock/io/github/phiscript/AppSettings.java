package io.github.phiscript;
import android.content.Context;
public final class AppSettings{public static boolean enabled=true;private static final java.util.Map<String,String> values=new java.util.HashMap<>();public AppSettings(Context c){}public boolean flag(String k,boolean d){return enabled;}public String text(String k,String fallback){String v=values.get(k);return v==null?fallback:v;}public void save(String k,String value){values.put(k,value);}}
