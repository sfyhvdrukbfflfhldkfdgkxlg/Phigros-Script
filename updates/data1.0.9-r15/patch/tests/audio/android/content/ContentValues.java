package android.content;

import java.util.HashMap;

public final class ContentValues {
    final HashMap<String,Integer> data = new HashMap<>();
    public void put(String key, Integer value) { data.put(key, value); }
}
