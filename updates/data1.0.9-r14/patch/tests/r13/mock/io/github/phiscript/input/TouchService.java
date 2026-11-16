package io.github.phiscript.input;
public class TouchService {
    public static final Companion Companion=new Companion();public static boolean foreground=true;
    public static final class Companion {public TouchService getCurrent(){return new TouchService();}}
    public boolean getGameForeground(){return foreground;}
}
