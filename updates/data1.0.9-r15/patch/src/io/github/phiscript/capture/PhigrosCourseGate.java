package io.github.phiscript.capture;

/** Three independent songs. A confirmed non-gameplay interval separates starts,
 * including courses that repeat the same song/difficulty. Never guesses a retry. */
public final class PhigrosCourseGate {
    public enum Decision { WAIT, READY, PAUSED, RESULT, COMPLETE }
    private final AutoEvidence boundaryEvidence=new AutoEvidence(),identityEvidence=new AutoEvidence();
    private boolean boundary,playing;
    private int completed;
    private long last=-1,firstHud=-1;
    private String candidate,previousIdentity;
    public int stage(){return completed+1;}
    public long firstHud(){return firstHud;}
    public Decision observe(long captured,long now,boolean hud,boolean menu,boolean result,String identity){
        if(completed==3)return Decision.COMPLETE;
        if(captured<=last||captured>now||now-captured>900)return Decision.WAIT;
        if(last>=0&&captured-last>2000){boundaryEvidence.reset();identityEvidence.reset();firstHud=-1;candidate=null;}
        last=captured;
        if(menu)return Decision.PAUSED;
        if(result)return Decision.RESULT;
        if(playing)return Decision.WAIT;
        if(!hud){
            identityEvidence.reset();candidate=null;firstHud=-1;
            if(boundaryEvidence.observe("transition",captured,now))boundary=true;
            return Decision.WAIT;
        }
        boundaryEvidence.reset();
        if(!boundary){
            // Some versions keep the pause HUD visible across course songs.
            // A different uniquely matched chart after a finished player is
            // a boundary too; the previous chart can never authorize a replay.
            if(completed==0||identity==null||identity.equals(previousIdentity)){identityEvidence.reset();candidate=null;firstHud=-1;return Decision.WAIT;}
            if(firstHud<0)firstHud=captured;
            candidate=identity;
            if(!identityEvidence.observe(identity,captured,now))return Decision.WAIT;
            boundary=true;return Decision.READY;
        }
        if(firstHud<0)firstHud=captured;
        if(identity==null){identityEvidence.reset();candidate=null;return Decision.WAIT;}
        if(candidate!=null&&!candidate.equals(identity)){identityEvidence.reset();firstHud=captured;}
        candidate=identity;
        return identityEvidence.observe(identity,captured,now)?Decision.READY:Decision.WAIT;
    }
    public void playing(){
        if(!boundary||candidate==null||completed>=3)throw new IllegalStateException("课题曲目尚未确认");
        playing=true;
    }
    public void finished(boolean observedTransition){
        if(!playing)throw new IllegalStateException("课题曲目尚未开始");
        completed++;playing=false;boundary=observedTransition;previousIdentity=candidate;
        firstHud=-1;candidate=null;identityEvidence.reset();boundaryEvidence.reset();
    }
    public boolean complete(){return completed==3;}
}
