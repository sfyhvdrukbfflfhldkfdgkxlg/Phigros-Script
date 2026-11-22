package io.github.phiscript.record;

import android.content.Context;
import io.github.phiscript.AppSettings;

/** Shared persisted choices for automatic and manual recording. */
public final class RecordingPreferences {
    private RecordingPreferences(){}
    public static RecordingPolicy.Profile load(Context context){
        AppSettings prefs=new AppSettings(context);
        return RecordingPolicy.stored(prefs.text("recording_resolution","1080"),prefs.text("recording_fps","60"));
    }
    public static void save(Context context,RecordingPolicy.Profile profile){
        AppSettings prefs=new AppSettings(context);
        prefs.save("recording_resolution",String.valueOf(profile.shortEdge));
        prefs.save("recording_fps",String.valueOf(profile.fps));
    }
    public static boolean gameAudioEnabled(Context context){
        return Boolean.parseBoolean(new AppSettings(context).text("recording_game_audio","false"));
    }
    static void setGameAudio(Context context,boolean enabled){
        new AppSettings(context).save("recording_game_audio",Boolean.toString(enabled));
    }
}
