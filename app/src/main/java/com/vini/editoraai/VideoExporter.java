package com.vini.editoraai;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@UnstableApi
public final class VideoExporter {
    public interface Listener {
        void success(File mp4);
        void error(String detail);
    }

    private Transformer transformer;
    private final ProgressHolder progressHolder = new ProgressHolder();
    private File output;

    public void start(Context context, Uri videoUri, Uri musicUri, long startMs, long endMs,
                      List<Caption> captions, Listener callback) {
        output = new File(context.getCacheDir(), "editora_export_" + System.currentTimeMillis() + ".mp4");
        if (output.exists()) output.delete();

        MediaItem.ClippingConfiguration clip = new MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build();
        MediaItem video = new MediaItem.Builder().setUri(videoUri).setClippingConfiguration(clip).build();

        List<Effect> videoEffects = new ArrayList<>();
        videoEffects.add(Presentation.createForWidthAndHeight(720, 1280,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
        if (!captions.isEmpty()) {
            List<TextureOverlay> overlays = Collections.singletonList(new CaptionOverlay(captions, startMs));
            videoEffects.add(new OverlayEffect(overlays));
        }
        EditedMediaItem editedVideo = new EditedMediaItem.Builder(video)
                .setEffects(new Effects(Collections.emptyList(), videoEffects)).build();
        EditedMediaItemSequence main = EditedMediaItemSequence.withAudioAndVideoFrom(
                Collections.singletonList(editedVideo));

        Composition composition;
        if (musicUri != null) {
            EditedMediaItem audioItem = new EditedMediaItem.Builder(MediaItem.fromUri(musicUri)).build();
            EditedMediaItemSequence track = EditedMediaItemSequence.withAudioFrom(
                    Collections.singletonList(audioItem)).buildUpon().setIsLooping(true).build();
            composition = new Composition.Builder(main, track).build();
        } else {
            composition = new Composition.Builder(main).build();
        }
        transformer = new Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener() {
                    @Override public void onCompleted(Composition comp, ExportResult result) {
                        callback.success(output);
                    }
                    @Override public void onError(Composition comp, ExportResult result, ExportException exception) {
                        if (output.exists()) output.delete();
                        callback.error(exception.getMessage() == null ? exception.toString() : exception.getMessage());
                    }
                }).build();
        transformer.start(composition, output.getAbsolutePath());
    }

    public int progress() {
        if (transformer == null) return 0;
        int status = transformer.getProgress(progressHolder);
        return status == Transformer.PROGRESS_STATE_AVAILABLE ? progressHolder.progress : -1;
    }

    public void cancel() {
        if (transformer != null) transformer.cancel();
        transformer = null;
        if (output != null && output.exists()) output.delete();
    }
}
