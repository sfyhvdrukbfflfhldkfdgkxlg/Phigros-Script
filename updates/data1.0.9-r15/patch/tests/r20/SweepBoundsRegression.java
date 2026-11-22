package io.github.phiscript.engine;

import java.util.Collections;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/** Compare import-only sweep rejection with the regular exact checks. */
public final class SweepBoundsRegression {
    private static JSONObject xy(double x, double y) throws Exception {
        return new JSONObject().put("x", x).put("y", y);
    }

    private static Chart chart(boolean subtract, boolean varying, int ease) throws Exception {
        Note note = new Note(1, 1, .1, 1, 0, 1, 0, true, 0);
        Chart c = Chart.Companion.fromNative$app_debug(Collections.singletonList(note), .125,
                (n, t, v) -> new Point(250, 300), (n, t, v) -> new Point(0, 1));
        JSONArray positions = new JSONArray();
        for (int i = 0; i < 3; i++)
            positions.put(new JSONObject().put("time", i * .5).put("easeTypeX", ease)
                    .put("easeTypeY", 14 - ease).put("endPosition", xy(.25 + i * .17, .61 - i * .08)));
        JSONArray scales = new JSONArray().put(new JSONObject().put("time", 0).put("scale", xy(-1.4, 2.1)));
        JSONArray rotations = new JSONArray().put(new JSONObject().put("time", 0).put("rotation", 28));
        if (varying) {
            scales.put(new JSONObject().put("time", 1).put("scale", xy(.6, .5)));
            rotations.put(new JSONObject().put("time", 1).put("rotation", 328));
        }
        JSONObject area = new JSONObject().put("bottomLeftPercentage", xy(.32,.29))
                .put("topRightPercentage", xy(.4,.37)).put("appearTime", .0625)
                .put("disappearTime", 1).put("isSubtract", subtract).put("moveEvents", positions)
                .put("scaleEvents", scales).put("rotateEvents", rotations);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList", new JSONArray().put(area)));
        BlockAreaSet.bind(c);
        return c;
    }

    public static void main(String[] args) throws Exception {
        Random random = new Random(1020721);
        int skipped = 0, checked = 0;
        for (int ease = 0; ease < 15; ease++) {
            Chart c = chart(ease % 2 == 0, ease == 12, ease);
            for (Viewport v : new Viewport[]{new Viewport(0,0,1000,600), new Viewport(77,22,780,611)}) {
                for (int i = 0; i < 160; i++) {
                    double from = i % 3 == 0 ? i % 17 / 16.0 - c.getOffsetSeconds() : random.nextDouble() - .13;
                    double to = from + (i % 7 == 0 ? 0 : random.nextDouble() * .04);
                    Point a = new Point(random.nextFloat() * 1050, random.nextFloat() * 650);
                    Point b = new Point(a.getX() + random.nextFloat() * 180 - 90,
                            a.getY() + random.nextFloat() * 120 - 60);
                    if (BlockAreaSet.mayTouchPath(c, v, a, b, from, to)) continue;
                    skipped++;
                    if (BlockAreaSet.crossesTranslation(c, v, a, b, from, to))
                        throw new AssertionError("broad phase missed a swept intersection");
                    for (int k = 0; k <= 16; k++) {
                        float f = k / 16f;
                        Point p = new Point(a.getX() + (b.getX() - a.getX()) * f,
                                a.getY() + (b.getY() - a.getY()) * f);
                        Point rounded = new Point(Math.round(p.getX()), Math.round(p.getY()));
                        double t = from + (to - from) * f;
                        if (BlockAreaSet.touchesNoise(c,v,p,t) || BlockAreaSet.touchesNoise(c,v,rounded,t))
                            throw new AssertionError("broad phase missed a footprint or its rounded pixel");
                        checked++;
                    }
                }
            }
        }
        if (skipped < 1000) throw new AssertionError("fast rejection was bypassed");
        System.out.println("PASS " + skipped + " sweep rejections and " + checked
                + " exact/rounded footprint checks over offsets, all easings, aspects and bucket boundaries.");
    }
}
