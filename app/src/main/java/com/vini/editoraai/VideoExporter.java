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

    private File intermediate;
    private boolean editingFirstPass=false;
    private volatile boolean cancelled=false;

    /**
     * Exportação em duas etapas para cortes.
     * Etapa 1: monta um único vídeo limpo com áudio sincronizado.
     * Etapa 2: queima legendas, FX, marca d'água e mixagem na linha de tempo FINAL.
     *
     * Isso evita aplicar overlays locais com timestamps da composição inteira,
     * principal causa de legendas fora do lugar na versão antiga.
     */
    public void start(Context context, Uri videoUri, Uri musicUri, long startMs, long endMs,
                      List<Caption> captions, List<MediaCue> mediaCues, int style, int animation,
                      int font, int size, int position, int voiceVolume, int musicVolume,
                      long musicDelay, boolean ducking, int duckPercent, boolean watermark,
                      int watermarkCorner, int watermarkSize, int watermarkAlpha,
                      List<SmartCuts.Cut> cuts, int visualFx, Listener listener) {
        cancelled=false;
        editingFirstPass=false;
        removeTemp();
        output=new File(context.getCacheDir(),
                "editora_export_"+System.currentTimeMillis()+".mp4");
        if(output.exists())output.delete();
        List<SmartCuts.Span> kept=SmartCuts.kept(startMs,endMs,
                cuts==null?Collections.emptyList():cuts);
        if(kept.isEmpty()){
            listener.error("Não sobrou vídeo depois dos cortes. Desfaça algumas escolhas.");return;
        }
        if(kept.size()>12){
            listener.error("No máximo 11 cortes por exportação para preservar a estabilidade.");return;
        }
        if(SmartCuts.duration(kept)<700){
            listener.error("O resultado ficaria muito curto. Revise os cortes.");return;
        }
        if(kept.size()==1){
            SmartCuts.Span item=kept.get(0);
            renderFinal(context,videoUri,musicUri,item.startMs,item.endMs,
                    captions,mediaCues,style,animation,font,size,position,voiceVolume,
                    musicVolume,musicDelay,ducking,duckPercent,watermark,
                    watermarkCorner,watermarkSize,watermarkAlpha,
                    Collections.emptyList(),visualFx,listener);
            return;
        }
        // Primeiro junta vídeo + áudio brutos, sem efeitos nem música.
        editingFirstPass=true;
        intermediate=new File(context.getCacheDir(),"editora_join_"+System.currentTimeMillis()+".mp4");
        if(intermediate.exists())intermediate.delete();
        ArrayList<EditedMediaItem> items=new ArrayList<>();
        for(SmartCuts.Span span:kept){
            MediaItem clip=new MediaItem.Builder().setUri(videoUri)
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(span.startMs)
                            .setEndPositionMs(span.endMs)
                            .build()).build();
            items.add(new EditedMediaItem.Builder(clip).build());
        }
        Composition cutComposition=new Composition.Builder(
                new EditedMediaItemSequence.Builder(items).build()).build();
        final List<Caption> remappedCaptions=SmartCuts.mapCaptions(captions,kept);
        final List<MediaCue> remappedCues=SmartCuts.mapCues(mediaCues,kept);
        final List<Long> joins=SmartCuts.joinPositions(kept);
        final long estimatedDuration=SmartCuts.duration(kept);
        transformer=new Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition composition,ExportResult result){
                        if(cancelled)return;
                        editingFirstPass=false;
                        Uri joined=Uri.fromFile(intermediate);
                        long joinedDuration=durationOf(context,joined,estimatedDuration);
                        renderFinal(context,joined,musicUri,0,joinedDuration,
                                remappedCaptions,remappedCues,style,animation,font,size,
                                position,voiceVolume,musicVolume,musicDelay,ducking,
                                duckPercent,watermark,watermarkCorner,watermarkSize,
                                watermarkAlpha,joins,visualFx,listener);
                    }
                    @Override public void onError(Composition composition,ExportResult result,
                                                  ExportException error){
                        editingFirstPass=false;
                        removeTemp();
                        if(!cancelled)listener.error("Erro ao juntar os trechos: "+
                                safeMessage(error));
                    }
                }).build();
        try {
            transformer.start(cutComposition,intermediate.getAbsolutePath());
        }catch(Exception ex){
            editingFirstPass=false;
            removeTemp();
            listener.error("Não foi possível iniciar a montagem dos cortes: "+safeMessage(ex));
        }
    }

    private static String safeMessage(Throwable ex){
        return ex.getMessage()==null?ex.toString():ex.getMessage();
    }

    private long durationOf(Context context,Uri uri,long fallback){
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{
            retriever.setDataSource(context,uri);
            String ms=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return ms==null?fallback:Math.max(700,Long.parseLong(ms));
        }catch(Exception ignored){return fallback;}
        finally{try{retriever.release();}catch(Exception ignored){}}
    }

    private void renderFinal(Context context,Uri source,Uri musicUri,long startMs,long endMs,
                             List<Caption> captions,List<MediaCue> mediaCues,
                             int style,int animation,int font,int size,int position,
                             int voiceVolume,int musicVolume,long musicDelay,
                             boolean ducking,int duckPercent,boolean watermark,
                             int watermarkCorner,int watermarkSize,int watermarkAlpha,
                             List<Long> joins,int visualFx,Listener listener){
        if(cancelled)return;
        MediaItem mainMedia=new MediaItem.Builder().setUri(source)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()).build();
        ArrayList<Effect> videoEffects=new ArrayList<>();
        videoEffects.add(Presentation.createForWidthAndHeight(720,1280,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
        List<TextureOverlay> overlays=new ArrayList<>();
        try{
            List<MediaCue> pictures=new ArrayList<>();
            for(MediaCue cue:mediaCues) if(cue.isImage() && cue.endMs>startMs
                    && cue.startMs<endMs)pictures.add(cue);
            if(!pictures.isEmpty())overlays.add(new MediaCueOverlay(context,pictures,startMs));
            if(!captions.isEmpty())overlays.add(new CaptionOverlay(context,
                    captions,startMs,style,animation,font,size,position));
            if(watermark)overlays.add(new WatermarkOverlay(context,
                    watermarkCorner,watermarkSize,watermarkAlpha));
            if(visualFx>0)overlays.add(new VisualFxOverlay(visualFx,
                    endMs-startMs,joins));
        }catch(Exception ex){
            removeTemp();
            listener.error("Falha ao preparar os efeitos: "+safeMessage(ex));
            return;
        }
        if(!overlays.isEmpty())videoEffects.add(new OverlayEffect(overlays));
        List<androidx.media3.common.audio.AudioProcessor> voiceFx=new ArrayList<>();
        if(voiceVolume!=100)voiceFx.add(new SmartMixAudioProcessor(
                voiceVolume/100f,voiceVolume/100f,false,0,startMs,null,false));
        EditedMediaItem main=new EditedMediaItem.Builder(mainMedia)
                .setEffects(new Effects(voiceFx,videoEffects)).build();
        ArrayList<EditedMediaItemSequence> sequences=new ArrayList<>();
        sequences.add(new EditedMediaItemSequence.Builder(main).build());
        if(musicUri!=null && musicVolume>0 && musicDelay<endMs-startMs){
            try{
                sequences.add(makeMusic(context,musicUri,startMs,endMs,musicDelay,
                        musicVolume,ducking,duckPercent,captions));
            }catch(Exception ex){
                removeTemp();
                listener.error("Não foi possível preparar a música: "+safeMessage(ex));
                return;
            }
        }
        try{
            EditedMediaItemSequence sounds=makeSounds(context,mediaCues,startMs,endMs);
            if(sounds!=null)sequences.add(sounds);
        }catch(Exception ex){
            removeTemp();
            listener.error("Não foi possível preparar efeitos sonoros: "+safeMessage(ex));
            return;
        }
        Composition composition=new Composition.Builder(sequences).build();
        transformer=new Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition composition,ExportResult result){
                        if(cancelled)return;
                        removeTemp();
                        listener.success(output);
                    }
                    @Override public void onError(Composition composition,ExportResult result,
                                                  ExportException error){
                        removeTemp();
                        if(output!=null&&output.exists())output.delete();
                        if(!cancelled)listener.error("Falha no render final: "+safeMessage(error));
                    }
                }).build();
        try{transformer.start(composition,output.getAbsolutePath());}
        catch(Exception ex){
            removeTemp();
            listener.error("Erro ao iniciar render final: "+safeMessage(ex));
        }
    }

    private void removeTemp(){
        if(intermediate!=null&&intermediate.exists())intermediate.delete();
        intermediate=null;
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
        if(transformer==null)return 0;
        int status=transformer.getProgress(progressHolder);
        if(status!=Transformer.PROGRESS_STATE_AVAILABLE)return -1;
        return editingFirstPass?Math.min(49,progressHolder.progress/2)
                : (intermediate!=null?50+progressHolder.progress/2:progressHolder.progress);
    }

    public void cancel() {
        cancelled=true;
        if(transformer!=null)transformer.cancel();
        transformer=null;
        removeTemp();
        if(output!=null&&output.exists())output.delete();
    }
}
