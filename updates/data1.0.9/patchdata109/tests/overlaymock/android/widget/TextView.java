package android.widget;
import android.view.View;import android.text.TextUtils;
public class TextView extends View {public CharSequence text="";public int lines;public float textSize;public void setText(CharSequence s){text=s;}public void setTextSize(int unit,float n){textSize=n;}public void setMaxLines(int n){lines=n;}public void setEllipsize(TextUtils.TruncateAt t){}}
