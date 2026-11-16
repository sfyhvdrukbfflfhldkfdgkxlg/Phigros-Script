package io.github.phiscript.assets;
import java.util.concurrent.atomic.AtomicBoolean;
public class LiveDownloadTest{public static void main(String[] a)throws Exception{
 byte[] bytes=FeijipanSource.download("eTyQ30l7","65979776813",new AtomicBoolean(),null);
 org.json.JSONObject root=new org.json.JSONObject(new String(bytes,"UTF-8"));System.out.println("response keys="+root.keySet()+" msg="+root.optString("msg")+" code="+root.optString("code"));
 io.github.phiscript.engine.Chart c=io.github.phiscript.engine.Chart.Companion.parse(new String(bytes,"UTF-8"));
 System.out.println("PASS Java live Feijipan download bytes="+bytes.length+" notes="+c.getNotes().size());
}}
