import org.json.*;import io.github.phiscript.engine.BlockAreaSet;
public class NoiseIndexRegression{
 public static void main(String[] args)throws Exception{
  JSONArray areas=new JSONArray();for(int i=9999;i>=0;i--){double x=(i%100)/100.0,t=i*.02;
   areas.put(new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",x).put("y",.4)).put("topRightPercentage",new JSONObject().put("x",x+.006).put("y",.6)).put("appearTime",t).put("enableTime",t).put("disableTime",t+.01).put("disappearTime",t+.01));}
  Object chart=new Object();BlockAreaSet.capture(new JSONObject().put("blockAreaList",areas));BlockAreaSet.bind(chart);if(BlockAreaSet.count(chart)!=10000)throw new AssertionError("large noise list dropped");java.util.Random r=new java.util.Random(1289);int checks=1;
  for(int k=0;k<1000;k++){int i=r.nextInt(10000);double time=i*.02+.005;float x=(i%100)*10+3;
   if(!BlockAreaSet.covers(chart,x,250,0,0,1000,500,time))throw new AssertionError("active interval missed");checks++;
   if(BlockAreaSet.covers(chart,(x+20)%1000,250,0,0,1000,500,time))throw new AssertionError("inactive area leaked");checks++;
   if(BlockAreaSet.covers(chart,x,50,0,0,1000,500,time))throw new AssertionError("geometry leaked");checks++;
   if(BlockAreaSet.covers(chart,x,250,0,0,1000,500,i*.02+.01))throw new AssertionError("disabled boundary leaked");checks++;
  }
  System.out.println("PASS large noise interval index "+checks+" assertions; 10000 reversed intervals");
 }
}
