package io.github.phiscript.i18n;

import io.github.phiscript.assets.OnlinePhiraLibrary;
import io.github.phiscript.assets.PhiraPbcDecoder;
import io.github.phiscript.vision.SongMatcher;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class EnglishUiRegression {
    private static void expect(String actual,String expected){
        if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);
    }
    private static String invalidId(){
        try{OnlinePhiraLibrary.id("no-id");throw new AssertionError("Accepted an invalid chart ID");}
        catch(IllegalArgumentException expected){return expected.getMessage();}
    }
    private static String invalidPbc() {
        try {
            PhiraPbcDecoder.INSTANCE.toJson(new byte[0]);
            throw new AssertionError("Accepted an empty PBC chart");
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        }
    }
    public static void main(String[] args)throws Exception{
        OnlinePhiraLibrary library=new OnlinePhiraLibrary((url,limit,stop)->
            ("{\"id\":8825,\"name\":\"测试曲名\",\"level\":\"IN\",\"file\":\""+
                OnlinePhiraLibrary.API+"/files/sample.pez\"}").getBytes(StandardCharsets.UTF_8));
        OnlinePhiraLibrary.Entry entry=library.fetch(8825,null);
        UiText.selectForTest("en");
        expect(invalidPbc(),"Invalid PBC file size");
        expect(UiText.text("等待手动启用"),"Waiting for manual activation");
        expect(NativeMessages.text("匹配：")+"待确认游戏 / IN","Match: 待确认游戏 / IN");
        if (!SongMatcher.INSTANCE.isResult(Arrays.asList("最大连击", "准确率")))
            throw new AssertionError("English UI changed Chinese result-screen recognition");
        expect(UiText.text("预计算触点"),"Prepare touch points");
        expect(UiText.text("Phigros · 课题连续校准（三首）"),"Phigros · Three-song course");
        expect(UiText.text("删除已下载谱面缓存"),"Clear downloaded charts");
        expect(AdditionalEnglish.tr("剩余 ")+3+AdditionalEnglish.tr(" 个本地谱面。"),"Remaining: 3 local charts.");
        expect(entry.display(),"#8825  测试曲名 / IN\nCharter: Unknown");
        if(!invalidId().contains("8825")||invalidId().matches(".*[\\u3400-\\u9fff].*"))
            throw new AssertionError("Phira validation did not use English");
        UiText.selectForTest("zh");
        expect(invalidPbc(),"PBC 文件大小无效");
        expect(UiText.text("等待手动启用"),"等待手动启用");
        expect(UiText.text("预计算触点"),"预计算触点");
        expect(invalidId(),"请输入 Phira 谱面正整数编号，例如 8825");
        expect(entry.display(),"#8825  测试曲名 / IN\n谱师：未知");
        System.out.println("PASS English catalog hook and locale switching for library, course, dynamic status and validation.");
    }
}
