package com.vini.editoraai;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Mídias aprovadas, persistidas por vídeo sem afetar o projeto anterior. */
public final class MediaCueStore {
    private MediaCueStore(){}
    private static String key(Uri uri){return "cues_"+(uri==null?"none":uri.toString());}
    public static List<MediaCue> load(Context c,Uri video){
        ArrayList<MediaCue> cues=new ArrayList<>();
        try{
            String value=c.getSharedPreferences("analyst_cues",Context.MODE_PRIVATE)
                          .getString(key(video),"[]");
            JSONArray array=new JSONArray(value);
            for(int i=0;i<array.length();i++)cues.add(MediaCue.fromJson(array.getJSONObject(i)));
        }catch(Exception ignored){}
        return cues;
    }
    public static void save(Context c,Uri video,List<MediaCue> cues){
        JSONArray array=new JSONArray();
        for(MediaCue cue:cues)array.put(cue.toJson());
        c.getSharedPreferences("analyst_cues",Context.MODE_PRIVATE).edit()
                .putString(key(video),array.toString()).apply();
    }
}
