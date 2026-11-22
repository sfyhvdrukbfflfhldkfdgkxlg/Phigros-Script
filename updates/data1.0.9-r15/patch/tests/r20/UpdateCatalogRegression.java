package io.github.phiscript.update;

import org.json.JSONArray;
import org.json.JSONObject;

/** Exercise the update selector from the signed APK with public release API records. */
public final class UpdateCatalogRegression {
    private static final String BASE =
        "https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/";

    private static String apkUrl(String tag) {
        return BASE+"download/"+tag+"/PhigrosScript_"+tag+".apk";
    }

    private static JSONObject release(String tag, boolean draft, boolean prerelease) throws Exception {
        JSONObject apk = new JSONObject().put("state", "uploaded").put("size", 16384)
            .put("browser_download_url", apkUrl(tag));
        return new JSONObject().put("tag_name", tag).put("html_url", BASE+"tag/"+tag)
            .put("draft", draft).put("prerelease", prerelease).put("assets", new JSONArray().put(apk));
    }

    private static void expect(String installed, String tag, JSONObject... entries) throws Exception {
        JSONArray list = new JSONArray();
        for (JSONObject entry : entries) list.put(entry);
        UpdateCatalog.Release actual = UpdateCatalog.latest(list.toString(), installed);
        if (tag == null) {
            if (actual != null) throw new AssertionError("Unexpected update to "+actual.version);
        } else if (actual == null || !tag.equals(actual.version) || !apkUrl(tag).equals(actual.apk)) {
            throw new AssertionError("Expected stable APK for "+tag);
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length>0 && "--revisions".equals(args[0])){
            JSONObject r2=release("data1.1.3-r2",false,false);
            JSONObject r3=release("data1.1.3-r3",false,false);
            JSONObject r10=release("data1.1.3-r10",false,false);
            expect("data1.1.3","data1.1.3-r2",r2);
            expect("data1.1.3-r2",null,r2,release("data1.1.3",false,false));
            expect("data1.1.3-r2","data1.1.3-r3",r2,r3);
            expect("data1.1.3-r3",null,r3,r2);
            expect("data1.1.3-r2","data1.1.3-r10",r10,r2,release("data1.1.3-r20",false,true));
            expect("data1.1.3-r10",null,r2);
            expect("data1.1.3-r10","data1.1.4",r2,release("data1.1.4",false,false));
            System.out.println("PASS stable numeric revisions: first upgrade, same-version suppression, numeric ordering, previews and future releases.");
            return;
        }
        JSONObject previous = release("data1.1.0", false, true);
        JSONObject stable = release("data1.1.1", false, false);
        JSONObject futurePreview = release("data1.2.0", false, true);
        JSONObject draft = release("data1.3.0", true, false);
        JSONObject noApk = release("data1.4.0", false, false).put("assets", new JSONArray());
        expect("data1.0.9-r15", null, previous, futurePreview, draft, noApk);
        expect("data1.1.0", "data1.1.1", futurePreview, draft, previous, stable, noApk);
        expect("data1.1.1", null, stable, previous, futurePreview);
        expect("data1.2.0", null, stable);
        System.out.println("PASS stable release updates: ignore drafts/pre-releases and missing APKs, numeric upgrade, same-version and downgrade suppression.");
    }
}
