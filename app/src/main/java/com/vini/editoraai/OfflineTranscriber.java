package com.vini.editoraai;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Transcreve o áudio do vídeo via MediaCodec + Vosk, sem microfone, nuvem ou chave. */
public final class OfflineTranscriber {
    public interface Progress { void report(int percent); }
    private static final int OUT_RATE = 16000;
    private OfflineTranscriber() { }

    public static List<Caption> transcribe(Context context, Uri videoUri, Progress progress) throws Exception {
        if (!OfflineModelManager.isInstalled(context)) throw new IOException("Primeiro, instale o modelo de português.");
        try (Model model = new Model(OfflineModelManager.modelDir(context).getAbsolutePath());
             Recognizer recognizer = new Recognizer(model, OUT_RATE)) {
            recognizer.setWords(true);
            ArrayList<Caption> output = new ArrayList<>();
            CaptionWords words = new CaptionWords(output);
            long decodedMs = decodeAudio(context, videoUri, recognizer, words, progress);
            words.accept(recognizer.getFinalResult(), decodedMs);
            words.flush();
            return output;
        }
    }

    private static long decodeAudio(Context context, Uri uri, Recognizer recognizer,
                                    CaptionWords words, Progress progress) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, uri, null);
            int track = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; inputFormat = f; break; }
            }
            if (track < 0 || inputFormat == null) throw new IOException("Este vídeo não tem áudio para legendar.");
            long durationUs = inputFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? inputFormat.getLong(MediaFormat.KEY_DURATION) : -1;
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            extractor.selectTrack(track);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            ResampledPcm pcm = null;
            int iterationsWithoutOutput = 0;
            int lastPercent = -1;
            while (!outputDone) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Transcrição interrompida.");
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inputIndex);
                        if (buffer == null) throw new IOException("Decoder sem buffer de entrada.");
                        buffer.clear();
                        int read = extractor.readSampleData(buffer, 0);
                        if (read < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            decoder.queueInputBuffer(inputIndex, 0, read, Math.max(0, pts), 0);
                            extractor.advance();
                        }
                    }
                }
                int index = decoder.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    pcm = new ResampledPcm(decoder.getOutputFormat(), recognizer, words);
                } else if (index >= 0) {
                    if (pcm == null) pcm = new ResampledPcm(decoder.getOutputFormat(), recognizer, words);
                    ByteBuffer buffer = decoder.getOutputBuffer(index);
                    if (buffer != null && info.size > 0) {
                        ByteBuffer data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                        data.position(info.offset);
                        data.limit(info.offset + info.size);
                        pcm.push(data.slice().order(ByteOrder.LITTLE_ENDIAN));
                    }
                    if (durationUs > 0 && info.presentationTimeUs >= 0) {
                        int pct = (int) Math.min(100, info.presentationTimeUs * 100 / durationUs);
                        if (pct >= lastPercent + 2) { lastPercent = pct; progress.report(pct); }
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    decoder.releaseOutputBuffer(index, false);
                    iterationsWithoutOutput = 0;
                } else if (++iterationsWithoutOutput > 1200) {
                    throw new IOException("Tempo limite ao decodificar o áudio.");
                }
            }
            if (pcm == null) throw new IOException("Não foi possível decodificar o áudio.");
            pcm.finish();
            progress.report(100);
            return pcm.getTotalMs();
        } finally {
            try { if (decoder != null) decoder.stop(); } catch (Exception ignored) { }
            if (decoder != null) decoder.release();
            extractor.release();
        }
    }

    /** Converte PCM 16-bit/float multicanal em PCM mono 16kHz por média de amostras. */
    private static final class ResampledPcm {
        final int channels;
        final int rate;
        final int encoding;
        final Recognizer recognizer;
        final CaptionWords captions;
        final byte[] samples = new byte[8192];
        int pos = 0;
        long phase = 0;
        double sum = 0;
        int count = 0;
        long outputSamples = 0;

        ResampledPcm(MediaFormat format, Recognizer recognizer, CaptionWords captions) throws IOException {
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING) ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    : AudioFormat.ENCODING_PCM_16BIT;
            if (channels < 1 || channels > 8 || rate < 8000 || rate > 192000)
                throw new IOException("Formato de áudio não suportado.");
            if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT)
                throw new IOException("PCM sem suporte (" + encoding + ").");
            this.recognizer = recognizer;
            this.captions = captions;
        }
        void push(ByteBuffer audio) throws Exception {
            int bytesPerSample = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
            while (audio.remaining() >= channels * bytesPerSample) {
                double mono = 0;
                for (int ch = 0; ch < channels; ch++) {
                    mono += encoding == AudioFormat.ENCODING_PCM_FLOAT
                            ? audio.getFloat() : audio.getShort() / 32768.0;
                }
                sum += mono / channels;
                count++;
                phase += OUT_RATE;
                while (phase >= rate) {
                    short sample = (short) Math.max(-32768, Math.min(32767, Math.round(32767.0 * sum / Math.max(1, count))));
                    samples[pos++] = (byte) (sample & 0xff);
                    samples[pos++] = (byte) ((sample >>> 8) & 0xff);
                    outputSamples++;
                    if (pos >= samples.length) flushSamples();
                    phase -= rate;
                    sum = 0;
                    count = 0;
                }
            }
        }
        long getTotalMs() { return outputSamples * 1000L / OUT_RATE; }
        void finish() throws Exception { flushSamples(); }
        void flushSamples() throws Exception {
            if (pos == 0) return;
            if (recognizer.acceptWaveForm(samples, pos))
                captions.accept(recognizer.getResult(), getTotalMs());
            pos = 0;
        }
    }

    /** Agrupa palavras da IA em legendas curtas de até 7 palavras / cerca de 3,6s. */
    private static final class CaptionWords {
        final List<Caption> captions;
        final StringBuilder text = new StringBuilder();
        long start = -1, end = 0;
        int words = 0;
        long lastResultMs = 0;
        CaptionWords(List<Caption> captions) { this.captions = captions; }

        void accept(String raw, long audioEndMs) throws Exception {
            JSONObject result = new JSONObject(raw);
            JSONArray arr = result.optJSONArray("result");
            boolean hasTimestamps = false;
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String word = item.optString("word", "").trim();
                if (word.isEmpty()) continue;
                hasTimestamps = true;
                long from = Math.round(item.optDouble("start", 0) * 1000);
                long to = Math.max(from + 120, Math.round(item.optDouble("end", from / 1000.0 + 0.3) * 1000));
                if (start >= 0 && (from - end > 650 || words >= 7 || to - start >= 3600)) flush();
                if (start < 0) start = from;
                if (text.length() > 0) text.append(' ');
                text.append(word);
                words++;
                end = to;
            }
            // Alguns resultados Vosk contêm apenas "text", sem lista "result".
            // Nesses casos não devemos descartar a transcrição inteira.
            if (!hasTimestamps) {
                String fallback = result.optString("text", "").trim();
                if (!fallback.isEmpty()) {
                    flush();
                    String[] tokens = fallback.split("\\s+");
                    int parts = Math.max(1, (tokens.length + 6) / 7);
                    long fromMs = Math.max(0, lastResultMs);
                    long untilMs = Math.max(audioEndMs, fromMs + parts * 450L);
                    for (int segment = 0; segment < parts; segment++) {
                        int lo = segment * 7;
                        int hi = Math.min(tokens.length, lo + 7);
                        StringBuilder phrase = new StringBuilder();
                        for (int k = lo; k < hi; k++) {
                            if (phrase.length() > 0) phrase.append(' ');
                            phrase.append(tokens[k]);
                        }
                        String caption = phrase.toString().trim();
                        if (!caption.isEmpty()) {
                            caption = Character.toUpperCase(caption.charAt(0)) + caption.substring(1);
                            long a = fromMs + (untilMs - fromMs) * segment / parts;
                            long b = fromMs + (untilMs - fromMs) * (segment + 1) / parts;
                            captions.add(new Caption(a, Math.max(a + 300, b), caption));
                        }
                    }
                }
            }
            lastResultMs = Math.max(lastResultMs, audioEndMs);
        }
        void flush() {
            if (text.length() > 0 && start >= 0) {
                String phrase = text.toString().trim();
                phrase = Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1);
                captions.add(new Caption(start, Math.max(end, start + 300), phrase));
            }
            text.setLength(0); start = -1; end = 0; words = 0;
        }
    }
}
