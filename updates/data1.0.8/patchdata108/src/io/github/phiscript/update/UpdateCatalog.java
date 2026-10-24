package io.github.phiscript.update;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Unauthenticated public release metadata only. No installation or executable payloads. */
public final class UpdateCatalog {
    public static final String REPO="sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script";
    public static final String RELEASES="https://github.com/"+REPO+"/releases";
    private static final Pattern VERSION=Pattern.compile("(?i)^(?:data|v)?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:[-+][A-Za-z0-9.-]+)?$");
    public static final class Release {
        public final String version,url,apk;
        public Release(String version,String url,String apk){this.version=version;this.url=url;this.apk=apk;}
    }
    private static int[] parts(String value){Matcher m=VERSION.matcher(value==null?"":value.trim());if(!m.matches())return null;return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))};}
    public static int compare(String a,String b){int[] x=parts(a),y=parts(b);if(x==null||y==null)throw new IllegalArgumentException("无法比较版本号");for(int i=0;i<3;i++){int c=Integer.compare(x[i],y[i]);if(c!=0)return c;}return 0;}
    private static boolean official(String value,boolean asset){
        try{URI u=new URI(value);String path=u.getPath();return "https".equals(u.getScheme())&&"github.com".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&u.getPort()==-1&&u.getQuery()==null&&u.getFragment()==null
            &&path.startsWith("/"+REPO+"/releases/"+(asset?"download/":"tag/"))&&!path.contains("/../")&&!path.contains("/./")&&(!asset||path.toLowerCase(java.util.Locale.ROOT).endsWith(".apk"));}
        catch(Exception e){return false;}
    }
    public static Release latest(String json,String current)throws Exception{
        if(json==null||json.length()>1048576)throw new IllegalArgumentException("更新信息过大");
        if(parts(current)==null)throw new IllegalArgumentException("无法读取当前版本号");
        JSONArray list=new JSONArray(json);Release best=null;
        for(int i=0;i<Math.min(100,list.length());i++){
            JSONObject r=list.optJSONObject(i);if(r==null||r.optBoolean("draft",false))continue;
            String tag=r.optString("tag_name",""),page=r.optString("html_url","");
            if(parts(tag)==null||compare(tag,current)<=0||!official(page,false))continue;
            JSONArray assets=r.optJSONArray("assets");String apk="";
            if(assets!=null)for(int j=0;j<assets.length();j++){
                JSONObject a=assets.optJSONObject(j);if(a==null)continue;String url=a.optString("browser_download_url","");
                if("uploaded".equals(a.optString("state","uploaded"))&&a.optLong("size",0)>0&&official(url,true)){apk=url;break;}
            }
            // Announcements without an installable APK must not trigger an update prompt.
            if(!apk.isEmpty()&&(best==null||compare(tag,best.version)>0))best=new Release(tag,page,apk);
        }
        return best;
    }
}
