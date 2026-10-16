package com.vini.editoraai;

import android.content.Context;
import android.media.MediaMetadataRetriever;
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
                      int size, int position, int voiceVolume, int musicVolume, long musicDelay,
                      boolean ducking, int duckPercent, boolean watermark, int watermarkCorner,
                      int watermarkSize,int watermarkAlpha, List<SmartCuts.Cut> cuts,
                      int visualFx, Listener callback) {
        output = new File(context.getCacheDir(), "editora_export_" + System.currentTimeMillis() + ".mp4");
        if (output.exists()) output.delete();

        List<SmartCuts.Span> kept=SmartCuts.kept(startMs,endMs,
                cuts==null?Collections.emptyList():cuts);
        if(kept.isEmpty()){callback.error("Todos os trechos foram removidos. Revise os cortes.");return;}
        if(kept.size()>16){callback.error("Há cortes demais (máximo de 15). Desmarque algumas pausas.");return;}
        long total=SmartCuts.duration(kept);
        if(total<500){callback.error("O vídeo restante ficou menor que meio segundo.");return;}
        ArrayList<EditedMediaItem> clips=new ArrayList<>();
        try{
            for(int i=0;i<kept.size();i++){
                SmartCuts.Span span=kept.get(i);
                MediaItem video=new MediaItem.Builder().setUri(videoUri)
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(span.startMs)
                                .setEndPositionMs(span.endMs).build()).build();
                List<Effect> videoFx=new ArrayList<>();
                videoFx.add(Presentation.createForWidthAndHeight(720,1280,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
                List<TextureOverlay> overlays=new ArrayList<>();
                ArrayList<MediaCue> pictures=new ArrayList<>();
                for(MediaCue cue:mediaCues)if(cue.isImage()
                        &&cue.endMs>span.startMs&&cue.startMs<span.endMs)pictures.add(cue);
                if(!pictures.isEmpty())overlays.add(
                        new MediaCueOverlay(context,pictures,span.startMs));
                ArrayList<Caption> localCaptions=new ArrayList<>();
                for(Caption c:captions)if(c.endMs>span.startMs&&c.startMs<span.endMs)
                    localCaptions.add(c);
                if(!localCaptions.isEmpty())overlays.add(new CaptionOverlay(context,
                        localCaptions,span.startMs,style,animation,font,size,position));
                if(watermark)overlays.add(new WatermarkOverlay(context,
                        watermarkCorner,watermarkSize,watermarkAlpha));
                if(visualFx>0)overlays.add(new VisualFxOverlay(visualFx,span.length(),i>0));
                if(!overlays.isEmpty())videoFx.add(new OverlayEffect(overlays));
                List<androidx.media3.common.audio.AudioProcessor> voiceFx=new ArrayList<>();
                if(voiceVolume!=100)voiceFx.add(new SmartMixAudioProcessor(
                        voiceVolume/100f,voiceVolume/100f,false,0L,span.startMs,null,false));
                clips.add(new EditedMediaItem.Builder(video)
                        .setEffects(new Effects(voiceFx,videoFx)).build());
            }
        }catch(Exception ex){
            callback.error("Não foi possível preparar os clipes: "+ex.getMessage());
            return;
        }
        EditedMediaItemSequence main=new EditedMediaItemSequence.Builder(clips).build();
        ArrayList<EditedMediaItemSequence> sequences=new ArrayList<>();
        sequences.add(main);
        List<Caption> mapped=SmartCuts.mapCaptions(captions,kept);
        if(musicUri!=null&&musicVolume>0&&musicDelay<total){
            try{
                sequences.add(makeMusic(context,musicUri,0,total,musicDelay,
                        musicVolume,ducking,duckPercent,mapped));
            }catch(Exception ex){
                callback.error("Não foi possível preparar a música: "+ex.getMessage());
                return;
            }
        }
        try{
            List<MediaCue> outputSounds=SmartCuts.mapSoundCues(mediaCues,kept);
            EditedMediaItemSequence sfx=makeSounds(context,outputSounds,0,total);
            if(sfx!=null)sequences.add(sfx);
        }catch(Exception ex){
            callback.error("Não foi possível preparar efeitos sonoros: "+ex.getMessage());
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

    /**
     * Cria a trilha musical iniciando após um silêncio, repetindo a música até o fim do vídeo.
     * Não usamos setIsLooping(true) com um gap: isso repetiria o silêncio a cada volta.
     */
    private EditedMediaItemSequence makeMusic(Context context,Uri uri,long trimStart,long trimEnd,
            long delay,int volume,boolean ducking,int duckPercent,List<Caption> captions)
            throws Exception {
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        long fileMs;
        try {
            retriever.setDataSource(context,uri);
            String length=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            fileMs=length==null?0L:Long.parseLong(length);
        } finally { retriever.release(); }
        if(fileMs<200)throw new Exception("Não consegui medir a duração da música. Tente um MP3 ou M4A local.");
        long total=trimEnd-trimStart;
        long cursor=Math.max(0,delay);
        EditedMediaItemSequence.Builder track=new EditedMediaItemSequence.Builder();
        track.experimentalSetForceAudioTrack(true);
        if(cursor>0)track.addGap(cursor*1000L);
        int items=0;
        while(cursor<total && items<100) {
            long part=Math.min(fileMs,total-cursor);
            if(part<30)break;
            MediaItem item=new MediaItem.Builder().setUri(uri)
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(part).build()).build();
            float mainGain=Math.max(0f,Math.min(1f,volume/100f));
            float lowGain=mainGain*Math.max(0f,Math.min(1f,duckPercent/100f));
            SmartMixAudioProcessor envelope=new SmartMixAudioProcessor(
                    mainGain,lowGain,ducking,cursor,trimStart,captions,items==0);
            EditedMediaItem music=new EditedMediaItem.Builder(item)
                    .setEffects(new Effects(Collections.singletonList(envelope),
                            Collections.emptyList())).build();
            track.addItem(music);
            cursor+=part;
            items++;
        }
        if(items==0)throw new Exception("O trecho da música ficou vazio.");
        return track.build();
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
