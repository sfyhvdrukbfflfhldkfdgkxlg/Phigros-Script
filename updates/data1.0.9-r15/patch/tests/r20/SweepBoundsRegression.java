package io.github.phiscript.engine;

import java.util.Collections;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/** Compare import and dispatch sweep rejection with the regular exact checks. */
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

    private static void inverseMovingHole() throws Exception {
        Note n=new Note(1,3,0,1,0,1,0,true,0);
        Chart chart=Chart.Companion.fromNative$app_debug(Collections.singletonList(n),0,
                (note,t,v)->new Point(500,300),(note,t,v)->new Point(0,1));
        JSONObject inverse=new JSONObject().put("bottomLeftPercentage",xy(-1,-1))
                .put("topRightPercentage",xy(2,2)).put("isSubtract",true).put("disappearTime",2);
        JSONObject hole=new JSONObject().put("bottomLeftPercentage",xy(.23,.21))
                .put("topRightPercentage",xy(.77,.8)).put("disappearTime",2);
        JSONArray move=new JSONArray(),rotate=new JSONArray(),scale=new JSONArray();
        for(int i=0;i<3;i++){
            move.put(new JSONObject().put("time",i*.5).put("endPosition",xy(.47+i*.08,.48+i*.06)).put("easeTypeX",3).put("easeTypeY",8));
            rotate.put(new JSONObject().put("time",i*.5).put("rotation",i*69).put("anchor",xy(.34,.46)).put("easeType",2));
            scale.put(new JSONObject().put("time",i*.5).put("scale",xy(1+i*.1,1-i*.09)).put("anchor",xy(.62,.51)).put("easeTypeX",1));
        }
        hole.put("moveEvents",move).put("rotateEvents",rotate).put("scaleEvents",scale);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(inverse).put(hole)));
        BlockAreaSet.bind(chart);
        Viewport view=new Viewport(22,13,1000,600);
        Random random=new Random(841485);int clear=0;
        for(int i=0;i<3000;i++){
            double start=i%5==0?i%65/64.0:random.nextDouble(),end=start+random.nextDouble()*.035;
            Point a=new Point((float)(160+random.nextDouble()*700),(float)(60+random.nextDouble()*450));
            Point b=new Point(a.getX()+random.nextFloat()*24-12,a.getY()+random.nextFloat()*24-12);
            if(BlockAreaSet.mayTouchPath(chart,view,a,b,start,end))continue;
            clear++;
            for(int j=0;j<=20;j++){
                double f=j/20.0,t=start+(end-start)*f;
                Point q=new Point((float)(a.getX()+(b.getX()-a.getX())*f),(float)(a.getY()+(b.getY()-a.getY())*f));
                if(BlockAreaSet.touchesNoise(chart,view,q,t) || BlockAreaSet.touchesNoise(chart,view,
                        new Point(Math.round(q.getX()),Math.round(q.getY())),t))
                    throw new AssertionError("certified an unsafe inverted moving hole");
            }
        }
        if(clear<300)throw new AssertionError("moving-hole clearance proof was bypassed: "+clear);
        System.out.println("PASS "+clear+" moving inverse-mask strokes including rotation/scale about distinct anchors");
    }

    private static void extremeCoordinates() throws Exception {
        Note n=new Note(1,3,0,1,0,1,0,true,0);
        Chart c=Chart.Companion.fromNative$app_debug(Collections.singletonList(n),0,
                (note,t,v)->new Point(500,300),(note,t,v)->new Point(0,1));
        Viewport view=new Viewport(0,0,1000,600);
        Point p=new Point(500,300);
        JSONObject area=new JSONObject().put("bottomLeftPercentage",xy(1e16,.4))
                .put("topRightPercentage",xy(1e16,.6)).put("disappearTime",2)
                .put("moveEvents",new JSONArray().put(new JSONObject().put("time",0).put("endPosition",xy(0,.5))));
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));
        BlockAreaSet.bind(c);
        if(!BlockAreaSet.touchesNoise(c,view,p,.25) || !BlockAreaSet.mayTouchPath(c,view,p,p,.25,.26))
            throw new AssertionError("A translated enormous source center lost the local touch displacement");
    }

    public static void main(String[] args) throws Exception {
        extremeCoordinates();
        inverseMovingHole();
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
