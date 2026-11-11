package io.github.phiscript.vision;
import android.graphics.Bitmap;import java.util.*;
public final class SongIdentifier implements AutoCloseable {
 public SongIdentifier(OcrConfig config){}public OcrLabels readLabels(Bitmap bitmap){return new OcrLabels(Collections.singletonList(bitmap.title),Collections.singletonList(bitmap.level),Collections.emptyList(),bitmap.result,bitmap.result,null);}public void close(){}
}
