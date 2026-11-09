package android.content;
public class Context {
 public static final String WINDOW_SERVICE="window";
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public Object getSystemService(String name){return new android.view.WindowManager(){};}
}
