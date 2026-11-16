package android.content;
import java.util.*;
public final class MemoryPreferences implements SharedPreferences {
 private final Map<String,Object> data=new HashMap<>();
 public Map<String,?> getAll(){return new HashMap<>(data);}public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
 @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return (Set<String>)data.getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)data.getOrDefault(k,d);}public long getLong(String k,long d){return (Long)data.getOrDefault(k,d);}public float getFloat(String k,float d){return (Float)data.getOrDefault(k,d);}public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}public boolean contains(String k){return data.containsKey(k);}
 public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
 public Editor edit(){return new Editor(){Map<String,Object> changes=new HashMap<>();boolean clear;
  public Editor putString(String k,String v){changes.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){changes.put(k,v);return this;}public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putLong(String k,long v){changes.put(k,v);return this;}public Editor putFloat(String k,float v){changes.put(k,v);return this;}public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}public Editor remove(String k){changes.put(k,null);return this;}public Editor clear(){clear=true;return this;}
  public boolean commit(){apply();return true;}public void apply(){if(clear)data.clear();for(Map.Entry<String,Object> e:changes.entrySet())if(e.getValue()==null)data.remove(e.getKey());else data.put(e.getKey(),e.getValue());}
 };}
}
