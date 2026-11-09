package io.github.phiscript.input;

/** Host model of the native selector's exact target/playing guard. */
public final class TouchService {
    public static TouchService current;
    public static final Companion Companion=new Companion();
    public static final class Companion {public TouchService getCurrent(){return current;}}
    public String foreground,target="org.flos.phira";
    public boolean playing,cancelled;
    public int selections;
    public String getForegroundPackage(){return foreground;}
    public String getTargetPackage(){return target;}
    public boolean getGameForeground(){return target.equals(foreground);}
    public void selectGame(String pkg){
        if(!pkg.equals("org.flos.phira")&&!pkg.equals("org.flos.phirapro")&&!pkg.equals("com.PigeonGames.Phigros"))throw new IllegalArgumentException();
        if(playing)throw new IllegalStateException("Please stop playback first");
        target=pkg;selections++;
    }
    public void foreground(String pkg){foreground=pkg;if(playing&&!target.equals(pkg))cancelled=true;}
}
