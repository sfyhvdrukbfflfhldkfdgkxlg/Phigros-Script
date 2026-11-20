package io.github.phiscript.engine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import org.json.*;

/** Compare the cached geometry with the geometry originally captured at import. */
public final class CompiledNoiseRegression {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
    private static Chart chart() {
        return Chart.Companion.fromNative$app_debug(Collections.singletonList(new Note(0,1,1,1,0,1,0,true,0)), .173,
                (n,t,v) -> { throw new AssertionError("Unexpected coordinate calculation"); },
                (n,t,v) -> { throw new AssertionError("Unexpected direction calculation"); });
    }
    private static byte[] encode(Chart chart) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BlockAreaSet.writeCompiled(new DataOutputStream(buffer), chart);
        return buffer.toByteArray();
    }
    private static void decode(byte[] bytes, Chart chart) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        BlockAreaSet.readCompiled(in, chart);
        check(in.read() == -1, "Unread noise bytes");
    }
    private static JSONObject xy(double x, double y) throws JSONException {
        return new JSONObject().put("x", x).put("y", y);
    }
    private static JSONObject rectangle(double radius, boolean subtract) throws JSONException {
        return new JSONObject().put("bottomLeftPercentage", xy(.5-radius,.5-radius))
                .put("topRightPercentage", xy(.5+radius,.5+radius)).put("isSubtract", subtract)
                .put("enableTime", 1).put("disableTime", 3);
    }
    private static JSONObject fixture(boolean invert) throws JSONException {
        JSONObject base = rectangle(.27, false);
        JSONArray scale = new JSONArray(), rotate = new JSONArray(), move = new JSONArray();
        for (int i = 0; i < 3; i++) {
            scale.put(new JSONObject().put("time",i+1).put("scale",xy(1+.1*i,1-.2*i))
                    .put("anchor",xy(.2*i,.3*i)).put("easeTypeX",2).put("easeTypeY",4));
            rotate.put(new JSONObject().put("time",i+1).put("rotation",47*i)
                    .put("anchor",xy(.8-.2*i,.6-.1*i)).put("easeType",13));
            move.put(new JSONObject().put("time",i+1).put("endPosition",xy(.4+.1*i,.7-.1*i)));
        }
        base.put("scaleEvents",scale).put("rotateEvents",rotate).put("moveEvents",move);
        JSONObject hole = rectangle(.1, true).put("enableTime",1.5).put("disableTime",2.5);
        return new JSONObject().put("blockAreaList",new JSONArray().put(base).put(hole))
                .put("phiscriptNoiseSingleInvert",invert);
    }
    private static void compare(JSONObject data, double start, double end, double step) throws Exception {
        Chart source = chart(), restored = chart();
        BlockAreaSet.capture(data);
        BlockAreaSet.bind(source);
        byte[] compiled = encode(source);
        decode(compiled, restored);
        check(BlockAreaSet.hasMetadata(source) == BlockAreaSet.hasMetadata(restored), "Metadata flag");
        check(BlockAreaSet.count(source) == BlockAreaSet.count(restored), "Area count");
        for (int width : new int[] {1000, 1333}) {
            Viewport v = new Viewport(12,7,width,600);
            for (double t = start; t < end; t += step) {
                for (int y = 0; y < 600; y += 53) {
                    for (int x = 0; x < width; x += 71) {
                        check(BlockAreaSet.covers(source,x,y,12,7,width,600,t) ==
                                BlockAreaSet.covers(restored,x,y,12,7,width,600,t), "Area at time "+t);
                    }
                }
                Point p = new Point(width/2f,300), q = new Point(width*.71f,330);
                check(BlockAreaSet.blockedAtChanges(source,v,p,q,t-.009,t+.018) ==
                        BlockAreaSet.blockedAtChanges(restored,v,p,q,t-.009,t+.018), "Clock guard");
                check(BlockAreaSet.crossesTranslation(source,v,p,q,t,t+.011) ==
                        BlockAreaSet.crossesTranslation(restored,v,p,q,t,t+.011), "Segment guard");
                long ms = Math.round(t*1000);
                check(BlockAreaSet.overlaps(source,ms-10,ms+20) ==
                        BlockAreaSet.overlaps(restored,ms-10,ms+20), "Activation times");
            }
        }
        if (BlockAreaSet.count(source) > 0) {
            int count = BlockAreaSet.count(restored);
            ByteBuffer.wrap(compiled).putInt(1,Integer.MAX_VALUE);
            try {
                decode(compiled, restored);
                throw new AssertionError("Corrupt noise section accepted");
            } catch (IOException expected) {
                check(BlockAreaSet.count(restored) == count, "Partial data replaced active geometry");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        compare(new JSONObject(),0,1,.5);
        compare(new JSONObject().put("blockAreaList",new JSONArray()),0,1,.5);
        compare(fixture(false),.8,3,.013);
        compare(fixture(true),.8,3,.013);
        for (String arg : args)
            compare(new JSONObject(new String(Files.readAllBytes(Paths.get(arg)),StandardCharsets.UTF_8)),
                    0,240,.19);
        System.out.println("Compiled noise geometry: "+checks+" parity checks passed");
    }
}
