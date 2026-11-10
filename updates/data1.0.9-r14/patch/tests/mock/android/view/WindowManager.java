
package android.view; public interface WindowManager {
 default WindowMetrics getMaximumWindowMetrics(){return new WindowMetrics();}
 default Display getDefaultDisplay(){return new Display();}}
