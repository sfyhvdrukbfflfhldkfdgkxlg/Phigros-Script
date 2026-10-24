package io.github.phiscript.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.i18n.UiText;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateChecker {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private UpdateChecker(){}
    public static void check(Activity activity){
        if(!BUSY.compareAndSet(false,true)){toast(activity,"正在检查更新…");return;}
        toast(activity,"正在检查更新…");
        new Thread(()->{
            try{
                String current=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
                UpdateCatalog.Release release=UpdateCatalog.latest(fetch(),current);
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    if(release==null){toast(activity,"已是最新发布版本");return;}
                    new AlertDialog.Builder(activity).setTitle(UiText.text("发现新版本")+" · "+release.version)
                        .setMessage(UiText.text("当前版本")+"："+current+"\n"+UiText.text("新版本")+"："+release.version+"\n\n"+UiText.text("从 GitHub Releases 下载更新，安装由系统确认。"))
                        .setPositiveButton(UiText.text("下载 APK"),(d,w)->open(activity,release.apk))
                        .setNeutralButton(UiText.text("版本说明"),(d,w)->open(activity,release.url))
                        .setNegativeButton(UiText.text("稍后"),null).show();
                });
            }catch(Exception e){
                RuntimeState.INSTANCE.log("检查更新失败："+e.getClass().getSimpleName());
                activity.runOnUiThread(()->{
                    if(!activity.isFinishing()&&!activity.isDestroyed())new AlertDialog.Builder(activity).setTitle(UiText.text("暂时无法检查更新"))
                        .setMessage(UiText.text("请检查网络，或打开 GitHub Releases 查看最新版本。"))
                        .setPositiveButton(UiText.text("打开 Releases"),(d,w)->open(activity,UpdateCatalog.RELEASES))
                        .setNegativeButton(UiText.text("关闭"),null).show();
                });
            }finally{BUSY.set(false);}
        },"phi-check-update").start();
    }
    private static String fetch()throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/"+UpdateCatalog.REPO+"/releases?per_page=30").openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Phigros-Script-update-check");
        try{
            if(c.getResponseCode()!=200)throw new java.io.IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1048576)throw new java.io.IOException("Update metadata limit");out.write(buffer,0,n);}
                return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            }
        }finally{c.disconnect();}
    }
    private static void open(Activity a,String url){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast(a,"没有可用的浏览器");}}
    private static void toast(Activity a,String text){if(!a.isFinishing()&&!a.isDestroyed())Toast.makeText(a,UiText.text(text),Toast.LENGTH_SHORT).show();}
}
