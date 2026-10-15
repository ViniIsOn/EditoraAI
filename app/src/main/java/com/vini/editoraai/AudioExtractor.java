package com.vini.editoraai;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Lossless extraction of the compressed audio track, without uploading the picture. */
public final class AudioExtractor {
    private AudioExtractor() { }

    public static File extract(Context context, Uri inputUri) throws IOException {
        File output = new File(context.getCacheDir(), "transcricao_" + System.currentTimeMillis() + ".m4a");
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            extractor.setDataSource(context, inputUri, null);
            int inputTrack = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    inputTrack = i;
                    format = fmt;
                    break;
                }
            }
            if (inputTrack < 0 || format == null) throw new IOException("Este vídeo não tem uma faixa de áudio.");
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!"audio/mp4a-latm".equals(mime)) {
                throw new IOException("Áudio " + mime + " não é compatível com extração direta. Use um vídeo com áudio AAC.");
            }
            extractor.selectTrack(inputTrack);
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outputTrack = muxer.addTrack(format);
            muxer.start();
            started = true;
            int bufferSize = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                    ? Math.max(262144, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)) : 1024 * 1024;
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                buffer.clear();
                int read = extractor.readSampleData(buffer, 0);
                if (read < 0) break;
                info.offset = 0;
                info.size = read;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(outputTrack, buffer, info);
                extractor.advance();
                if (output.length() > 24L * 1024 * 1024) throw new IOException("Áudio muito grande para a API (máximo 25 MB). Tente um vídeo menor.");
            }
            return output;
        } catch (Exception e) {
            output.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Falha ao extrair áudio: " + e.getMessage(), e);
        } finally {
            extractor.release();
            if (muxer != null) {
                if (started) { try { muxer.stop(); } catch (Exception ignored) { } }
                try { muxer.release(); } catch (Exception ignored) { }
            }
        }
    }
}
