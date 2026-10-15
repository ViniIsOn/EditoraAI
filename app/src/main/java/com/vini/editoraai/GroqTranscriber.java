package com.vini.editoraai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Sends only the compressed audio to Groq Whisper; the API key is never stored. */
public final class GroqTranscriber {
    private GroqTranscriber() { }

    public static List<Caption> transcribe(File audio, String apiKey) throws Exception {
        if (audio.length() > 25L * 1024L * 1024L)
            throw new IOException("O áudio excede 25 MB; escolha um vídeo mais curto.");
        String boundary = "editoraai" + System.nanoTime();
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(64 * 1024);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream out = conn.getOutputStream()) {
                part(out, boundary, "model", "whisper-large-v3-turbo");
                part(out, boundary, "language", "pt");
                part(out, boundary, "response_format", "verbose_json");
                part(out, boundary, "timestamp_granularities[]", "segment");
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fala.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                try (InputStream in = new BufferedInputStream(new FileInputStream(audio))) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String result = read(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                String message;
                try { message = new JSONObject(result).getJSONObject("error").optString("message", result); }
                catch (Exception ignored) { message = result; }
                throw new IOException("Whisper HTTP " + code + ": " + (message.length() > 220 ? message.substring(0, 220) : message));
            }
            JSONObject json = new JSONObject(result);
            JSONArray segments = json.optJSONArray("segments");
            List<Caption> captions = new ArrayList<>();
            if (segments != null) {
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject s = segments.optJSONObject(i);
                    if (s == null) continue;
                    long start = Math.round(s.optDouble("start", 0) * 1000);
                    long end = Math.round(s.optDouble("end", 0) * 1000);
                    String text = s.optString("text", "").trim();
                    if (!text.isEmpty()) captions.add(new Caption(start, Math.max(start + 300, end), text));
                }
            }
            if (captions.isEmpty() && !json.optString("text").trim().isEmpty()) {
                captions.add(new Caption(0, 5000, json.getString("text")));
            }
            return captions;
        } finally {
            conn.disconnect();
        }
    }

    private static void part(OutputStream out, String boundary, String name, String value) throws IOException {
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "Erro sem resposta do servidor";
        StringBuilder b = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String l;
            while ((l = reader.readLine()) != null) b.append(l).append('\n');
        }
        return b.toString();
    }
}
