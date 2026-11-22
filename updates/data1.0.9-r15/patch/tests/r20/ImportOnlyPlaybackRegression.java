package io.github.phiscript.input;

import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Note;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.root.InputBackend;
import io.github.phiscript.root.RootClient;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import android.graphics.Insets;
import android.view.WindowInsets;
import kotlin.Unit;

public final class ImportOnlyPlaybackRegression {
    private static boolean forbid;
    private static final Viewport VIEW = new Viewport(0,0,1000,600);

    private static void reject(TouchService service, Chart chart, Viewport viewport) throws Exception {
        AtomicBoolean playing = new AtomicBoolean();
        int rootConnections = RootClient.created;
        int sends = service.sends;
        forbid = true;
        try {
            ChartMultiTouchPlayer.play(service,chart,100000,viewport,new AtomicBoolean(),
                message->Unit.INSTANCE,playing,new AtomicBoolean());
            throw new AssertionError("Unprepared chart was accepted");
        } catch (IllegalStateException error) {
            if(!error.getMessage().contains("预计算触点"))throw error;
        } finally {
            forbid = false;
        }
        if(playing.get() || RootClient.created!=rootConnections || service.sends!=sends)
            throw new AssertionError("Unprepared chart started an input channel or leaked playback ownership");
    }

    public static void main(String[] args) throws Exception {
        portraitImportBounds();
        legacyNavigationBounds();
        TapMod.init(new android.content.Context());
        for(boolean root:new boolean[]{false,true}){
            InputBackend.rootEnabled=root;
            TouchService service=new TouchService();
            Chart chart=Chart.Companion.fromNative$app_debug(
                Collections.singletonList(new Note(0,3,.1,.5,.5,1,0,true,0)),0,
                (note,time,viewport)->{
                    if(forbid)throw new AssertionError("Playback miss evaluated chart coordinates");
                    return new Point(500,300);
                },
                (note,time,viewport)->{
                    if(forbid)throw new AssertionError("Playback miss evaluated judgement normal");
                    return new Point(0,1);
                });
            reject(service,chart,VIEW);
            service.prepareChart(chart,VIEW);
            reject(service,chart,new Viewport(0,0,999,600));
            boolean old=AppSettings.inward;
            AppSettings.inward=!old;
            try{reject(service,chart,VIEW);}finally{AppSettings.inward=old;}
        }
        InputBackend.rootEnabled=false;
        System.out.println("PASS landscape insets restored at import and six Root/accessibility misses rejected before planning, connection or dispatch.");
    }
    private static void legacyNavigationBounds() {
        int api = android.os.Build.VERSION.SDK_INT;
        boolean oldEdges = AppSettings.edges;
        try {
            android.os.Build.VERSION.SDK_INT = 29;
            AppSettings.edges = true;
            TouchService service = new TouchService();
            android.view.Display.usableWidth = 1000;
            android.view.Display.usableHeight = 540;
            TouchBounds bottom = TouchSafety.read(service,1000,600,VIEW).bounds;
            if (bottom.bottom != 537) throw new AssertionError("Bottom navigation strip was touchable");
            android.view.Display.usableHeight = 600;
            android.view.Display.usableWidth = 936;
            android.view.Display.rotation = 1;
            TouchBounds right = TouchSafety.read(service,1000,600,VIEW).bounds;
            if (right.right != 933 || right.left != 20) throw new AssertionError("Right navigation strip");
            android.view.Display.rotation = 3;
            TouchBounds left = TouchSafety.read(service,1000,600,VIEW).bounds;
            if (left.left != 66 || left.right != 979) throw new AssertionError("Left navigation strip");
            android.view.Display.usableWidth = 1000;
            TouchBounds restored = TouchSafety.read(new android.content.Context(),1000,600,VIEW,true).bounds;
            if (!PreparedTrajectory.same(left,restored)) throw new AssertionError("Portrait legacy import");
            AppSettings.edges = false;
            if (TouchSafety.read(service,1000,600,VIEW).bounds.left != 0)
                throw new AssertionError("Explicit edge guard setting ignored");
        } finally {
            android.os.Build.VERSION.SDK_INT = api;
            AppSettings.edges = oldEdges;
            android.view.Display.usableWidth = 1000;
            android.view.Display.usableHeight = 600;
            android.view.Display.rotation = 0;
        }
    }

    private static void portraitImportBounds(){
        AppSettings.edges=true;
        TouchService service=new TouchService();
        android.content.Context library=new android.content.Context();
        try {
            WindowInsets.bars=new Insets(90,12,0,0);
            TouchBounds game=TouchSafety.read(service,1000,600,VIEW).bounds;
            WindowInsets.bars=new Insets(0,90,0,0);
            TouchBounds prepared=TouchSafety.read(library,1000,600,VIEW,true).bounds;
            TouchBounds resized=TouchSafety.read(library,1200,600,new Viewport(0,0,1200,600),true).bounds;
            if(game.left!=92||!PreparedTrajectory.same(game,prepared)||resized.top!=92)
                throw new AssertionError("Portrait import did not use matching observed landscape insets");
        }finally{
            WindowInsets.bars=new Insets(0,0,0,0);
            AppSettings.edges=false;
        }
    }
}
