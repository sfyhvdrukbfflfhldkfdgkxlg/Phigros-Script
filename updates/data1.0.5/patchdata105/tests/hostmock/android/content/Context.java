package android.content;
public class Context {public static final String WINDOW_SERVICE="window";public Object manager;public Context app;public Object getSystemService(String s){return manager;}public String getPackageName(){return "io.github.phiscript";}public Context getApplicationContext(){return app==null?this:app;}}
