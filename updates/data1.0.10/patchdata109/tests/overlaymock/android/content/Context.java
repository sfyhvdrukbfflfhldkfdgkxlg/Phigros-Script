package android.content;
import android.view.WindowManager;
public class Context {public static final String WINDOW_SERVICE="window";public final android.view.FakeWindowManager window=new android.view.FakeWindowManager();public Object getSystemService(String name){return window;}}
