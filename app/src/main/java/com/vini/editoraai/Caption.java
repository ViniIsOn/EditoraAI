package com.vini.editoraai;

import org.json.JSONObject;

/** Timings are milliseconds measured against the ORIGINAL imported video. */
public final class Caption {
    public long startMs;
    public long endMs;
    public String text;

    public Caption(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text.trim();
    }

    public boolean contains(long ms) { return ms >= startMs && ms < endMs; }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try { obj.put("start", startMs); obj.put("end", endMs); obj.put("text", text); }
        catch (Exception ignored) { }
        return obj;
    }

    public static Caption fromJson(JSONObject obj) {
        return new Caption(obj.optLong("start"), obj.optLong("end"), obj.optString("text"));
    }
}
