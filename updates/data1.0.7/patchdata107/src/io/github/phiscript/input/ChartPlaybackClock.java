package io.github.phiscript.input;

/** A per-playback clock. Calibration takes effect at a scheduler boundary. */
public final class ChartPlaybackClock {
    private static final ThreadLocal<ChartPlaybackClock> bound=new ThreadLocal<>();
    private final long initialEpoch;
    private Slice current;
    private boolean calibrated,pending;
    private long targetEpoch;

    public ChartPlaybackClock(long epoch){
        initialEpoch=epoch;
        current=new Slice(0,-(double)epoch,1,Double.POSITIVE_INFINITY,epoch);
    }
    public static void bind(ChartPlaybackClock clock){
        if(clock==null||bound.get()!=null)throw new IllegalStateException("已有谱面时钟");
        bound.set(clock);
    }
    public static void unbind(){bound.remove();}
    static ChartPlaybackClock resolve(long epoch){
        ChartPlaybackClock clock=bound.get();
        return clock!=null&&clock.initialEpoch==epoch?clock:new ChartPlaybackClock(epoch);
    }
    public synchronized boolean calibrate(long epoch){
        if(epoch<0)throw new IllegalArgumentException("校准时刻无效");
        if(calibrated)return false;
        calibrated=pending=true;targetEpoch=epoch;return true;
    }
    public synchronized boolean hasCalibration(){return calibrated;}
    public synchronized Slice snapshot(long wall){
        if(pending){
            double time=current.value(wall),difference=wall-(double)targetEpoch-time;
            // Bounded slew avoids rewinding the event cursor, teleporting a held
            // finger, or compressing a 48 ms blue head below a 30 Hz frame.
            double rate=difference>0?1.25:difference<0?.75:1;
            double settled=rate==1?wall:wall+Math.abs(difference)/.25;
            current=new Slice(wall,time,rate,settled,targetEpoch);pending=false;
        }
        return current;
    }
    /** Immutable projection used for the entire submitted accessibility batch. */
    public static final class Slice {
        private final double wall,time,rate,settled;
        private final long epoch;
        private Slice(double wall,double time,double rate,double settled,long epoch){
            this.wall=wall;this.time=time;this.rate=rate;this.settled=settled;this.epoch=epoch;
        }
        private double value(long at){return at>=settled?at-(double)epoch:time+(at-wall)*rate;}
        public long chartAt(long at){return (long)Math.floor(value(at));}
        public long wallAt(long chart){
            double cut=settled-epoch;
            return (long)Math.ceil(chart>=cut?chart+(double)epoch:wall+(chart-time)/rate);
        }
    }
}
