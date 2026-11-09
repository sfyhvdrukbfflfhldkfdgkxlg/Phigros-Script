package io.github.phiscript.capture;

/** Projection revocation must be visible; normal teardown callbacks must stay quiet. */
public final class SessionStopPolicy {
    private SessionStopPolicy(){}
    public static boolean shouldReport(String reason,boolean alreadyStopped){
        return !alreadyStopped&&reason!=null&&(reason.equals("录屏已结束")||reason.contains("失败")||reason.contains("不可用"));
    }
}
