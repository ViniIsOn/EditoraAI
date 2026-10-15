package com.vini.editoraai;

import org.json.JSONObject;

/** Um item temporizado: imagem, meme ou efeito sonoro, nos tempos do vídeo original. */
public final class MediaCue {
    public String kind, uri, label, credit, sound;
    public long startMs, endMs;
    public int effect;
    public MediaCue(String kind, String uri, String label, String credit, String sound,
                    long startMs, long endMs, int effect) {
        this.kind=kind;this.uri=uri;this.label=label;this.credit=credit;this.sound=sound;
        this.startMs=startMs;this.endMs=endMs;this.effect=effect;
    }
    public boolean contains(long ms){return ms>=startMs&&ms<endMs;}
    public boolean isImage(){return "image".equals(kind)||"meme".equals(kind);}
    public JSONObject toJson(){
        JSONObject j=new JSONObject();
        try{
            j.put("kind",kind);j.put("uri",uri);j.put("label",label);
            j.put("credit",credit);j.put("sound",sound);
            j.put("start",startMs);j.put("end",endMs);j.put("effect",effect);
        }catch(Exception ignored){}
        return j;
    }
    public static MediaCue fromJson(JSONObject j) {
        return new MediaCue(j.optString("kind","image"),j.optString("uri",""),
                j.optString("label",""),j.optString("credit",""),
                j.optString("sound",""),j.optLong("start"),j.optLong("end"),
                j.optInt("effect"));
    }
}
