package io.github.phiscript.input;
import io.github.phiscript.capture.CourseEngineRegression;
public class GameForeground {
 public static boolean isForeground(TouchService service){
  if(CourseEngineRegression.scenario.equals("startup")&&CourseEngineRegression.foregroundChecks++<3){android.os.SystemClock.now+=80;return false;}
  return !CourseEngineRegression.leftGame;
 }
}
