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
import java.util.Comparator;
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
                      List<Caption> captions, List<MediaCue> mediaCues, int style, int animation, int font,
                      int size, int position, Listener callback) {
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
        List<TextureOverlay> overlays = new ArrayList<>();
        List<MediaCue> pictures = new ArrayList<>();
        for (MediaCue cue : mediaCues) if (cue.isImage() && cue.endMs > startMs
                && cue.startMs < endMs) pictures.add(cue);
        if (!pictures.isEmpty()) {
            try {
                overlays.add(new MediaCueOverlay(context, pictures, startMs));
            } catch (Exception e) {
                callback.error("Erro ao abrir imagem de apoio: "+e.getMessage());
                return;
            }
        }
        if (!captions.isEmpty()) {
            overlays.add(new CaptionOverlay(context, captions, startMs, style,
                    animation, font, size, position));
        }
        if (!overlays.isEmpty()) videoEffects.add(new OverlayEffect(overlays));
        EditedMediaItem editedVideo = new EditedMediaItem.Builder(video)
                .setEffects(new Effects(Collections.emptyList(), videoEffects)).build();
        EditedMediaItemSequence main = new EditedMediaItemSequence.Builder(editedVideo).build();

        ArrayList<EditedMediaItemSequence> sequences=new ArrayList<>();
        sequences.add(main);
        if (musicUri != null) {
            EditedMediaItem audioItem=new EditedMediaItem.Builder(MediaItem.fromUri(musicUri)).build();
            sequences.add(new EditedMediaItemSequence.Builder(audioItem).setIsLooping(true).build());
        }
        try {
            EditedMediaItemSequence sfx=makeSounds(context,mediaCues,startMs,endMs);
            if(sfx!=null) sequences.add(sfx);
        }catch(Exception ex){
            callback.error("Não foi possível preparar os efeitos sonoros: "+ex.getMessage());
            return;
        }
        Composition composition=new Composition.Builder(sequences).build();
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

    /** Media3 audio gaps let sound effects begin at precise offsets without pausing speech. */
    private EditedMediaItemSequence makeSounds(Context context,List<MediaCue> media,
                                               long startMs,long endMs)throws Exception {
        List<MediaCue> ordered=new ArrayList<>();
        for(MediaCue cue:media)if("sfx".equals(cue.kind)&&cue.endMs>startMs
                &&cue.startMs<endMs)ordered.add(cue);
        if(ordered.isEmpty())return null;
        Collections.sort(ordered,Comparator.comparingLong(c->c.startMs));
        EditedMediaItemSequence.Builder soundTrack=new EditedMediaItemSequence.Builder();
        // Necessário quando o primeiro trecho da faixa é um silêncio.
        soundTrack.experimentalSetForceAudioTrack(true);
        int itemCount=0;
        long cursorMs=0L;
        for(MediaCue cue:ordered){
            long relativeStart=Math.max(0,Math.max(startMs,cue.startMs)-startMs);
            if(relativeStart<cursorMs)continue; // never shift or double-play overlaps
            long gapMs=relativeStart-cursorMs;
            if(gapMs>0){
                soundTrack.addGap(gapMs*1000L);
            }
            Uri source;
            int soundLength;
            if("custom".equals(cue.sound)){
                source=Uri.parse(cue.uri);
                soundLength=(int)Math.min(1800,Math.max(250,Math.min(cue.endMs-cue.startMs,1800)));
            }else{
                int id=Integer.parseInt(cue.sound);
                source=SfxLibrary.file(context,id);
                soundLength=SfxLibrary.durationMs(id);
            }
            soundLength=(int)Math.min(soundLength,Math.max(0,endMs-startMs-relativeStart));
            if(soundLength<50)continue;
            MediaItem sound=new MediaItem.Builder().setUri(source)
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(soundLength).build()).build();
            soundTrack.addItem(new EditedMediaItem.Builder(sound).build());
            itemCount++;
            cursorMs=relativeStart+soundLength;
        }
        if(itemCount==0)return null;
        return soundTrack.build();
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
