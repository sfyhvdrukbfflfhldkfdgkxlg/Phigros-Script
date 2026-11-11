package io.github.phiscript.input;

import android.os.SystemClock;
import android.accessibilityservice.GestureDescription;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.PhiraImportData;
import io.github.phiscript.assets.PhiraImportDecoder;
import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.BlockAreaSet;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.root.InputBackend;
import io.github.phiscript.root.RootClient;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PerformanceReplay {
    private static final long EPOCH = 100000;
    private static final Viewport VIEW = new Viewport(0, 0, 1000, 600);

    private static boolean blocked(Chart chart, float x, float y, long when) {
        return BlockAreaSet.covers(chart, Math.round(x), Math.round(y), 0, 0, 1000, 600, (when-EPOCH)/1000.0);
    }

    private static float[] position(List<float[]> points, double fraction) {
        double length = 0;
        for (int i=1; i<points.size(); i++) {
            float[] a=points.get(i-1), b=points.get(i);
            length+=Math.hypot(b[0]-a[0],b[1]-a[1]);
        }
        double offset=length*fraction;
        for (int i=1; i<points.size(); i++) {
            float[] a=points.get(i-1), b=points.get(i);
            double segment=Math.hypot(b[0]-a[0],b[1]-a[1]);
            if (segment>0 && offset<=segment) {
                double f=offset/segment;
                return new float[]{(float)(a[0]+f*(b[0]-a[0])),(float)(a[1]+f*(b[1]-a[1]))};
            }
            offset-=segment;
        }
        return points.get(points.size()-1);
    }

    public static void main(String[] args) throws Exception {
        ContactRegression.clear();
        AppSettings.edges = true;
        AppSettings.values.put("tapmod_enabled", "true");
        Chart chart;
        try (FileInputStream input = new FileInputStream(args[0])) {
            PhiraImportData data = PhiraImportDecoder.INSTANCE.decode(input, "sample.pez");
            chart = SharedChartImport.parseJson(data.getNormalizedJson());
        }
        for (boolean root : new boolean[]{false, true}) {
            TouchService service = new TouchService();
            InputBackend.rootEnabled = root;
            SystemClock.now = EPOCH;
            List<String> status = new ArrayList<>();
            int[] samples={0,0};
            service.after=gesture -> {
                long duration=0;
                for (GestureDescription.StrokeDescription stroke:gesture.strokes)
                    duration=Math.max(duration,stroke.start+stroke.duration);
                long base=SystemClock.now-duration-2;
                for (GestureDescription.StrokeDescription stroke:gesture.strokes) {
                    for (long ms=1; ms<stroke.duration; ms+=4) {
                        float[] p=position(stroke.path.points,ms/(double)stroke.duration);
                        samples[0]++;
                        if (blocked(chart,p[0],p[1],base+stroke.start+ms)) samples[1]++;
                    }
                }
            };
            GesturePlan.preflight(chart, service, VIEW);
            ChartMultiTouchPlayer.play(service, chart, EPOCH, VIEW, new AtomicBoolean(),
                message -> { status.add(message); return kotlin.Unit.INSTANCE; },
                new AtomicBoolean(), new AtomicBoolean());
            String finish = root ? "Root 演奏完成" : "演奏完成";
            if (!status.contains(finish) || service.frameworkFailed || !service.roots.isEmpty()
                    || root && !RootClient.last.active.isEmpty()) {
                throw new AssertionError("Incomplete replay: " + status);
            }
            List<long[]> downs = root ? RootClient.last.heads : service.downs;
            int peak = root ? RootClient.last.peak : service.peak;
            if (downs.size() < 500 || peak > 10) throw new AssertionError("Unexpected physical contact count");
            int blocked = 0;
            for (long[] down : downs) {
                float x = Float.intBitsToFloat((int)down[2]), y = Float.intBitsToFloat((int)down[3]);
                double t = (down[1] - EPOCH) / 1000.0;
                if (BlockAreaSet.covers(chart, Math.round(x), Math.round(y), 0, 0, 1000, 600, t)) {
                    if(blocked < 4)System.err.println("unsafe DOWN t=" + t + " x=" + x + " y=" + y + " root=" + root);
                    blocked++;
                }
            }
            if(root) for(float[] p:RootClient.last.samples) {
                samples[0]++;
                if(blocked(chart,p[2],p[3],(long)p[0]))samples[1]++;
            }
            System.out.println((root ? "Root" : "Accessibility") + " completed notes=" + chart.getNotes().size()
                + " physicalDowns=" + downs.size() + " peakAllNoteTypes=" + peak + " blockedDowns=" + blocked+" positionSamples="+samples[0]+" blockedSamples="+samples[1]);
            if (blocked != 0 || samples[1] != 0) throw new AssertionError("Physical touch in noise");
        }
    }
}
