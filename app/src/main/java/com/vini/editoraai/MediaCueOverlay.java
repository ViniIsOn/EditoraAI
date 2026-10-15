package com.vini.editoraai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Movie;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.net.Uri;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.StaticOverlaySettings;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Renderiza imagens/memes aprovados em momentos escolhidos, sem remover a voz original. */
@UnstableApi
public final class MediaCueOverlay extends BitmapOverlay {
    private final List<MediaCue> cues=new ArrayList<>();
    private final Map<String,Bitmap> images=new HashMap<>();
    private final Map<String,Movie> gifs=new HashMap<>();
    private final long trimStartMs;
    private final OverlaySettings settings;
    private Bitmap output;
    private MediaCue prior;
    private long priorStep=Long.MIN_VALUE;

    public MediaCueOverlay(Context context,List<MediaCue> incoming,long trimStartMs)throws Exception{
        this.trimStartMs=trimStartMs;
        for(MediaCue cue:incoming){
            if(!cue.isImage())continue;
            Uri uri=Uri.parse(cue.uri);
            if(!images.containsKey(cue.uri)&&!gifs.containsKey(cue.uri)){
                try{
                    if(GifSupport.isGif(context,uri)){
                        Movie gif=GifSupport.load(context,uri);
                        if(gif!=null)gifs.put(cue.uri,gif);
                    }else{
                    try(InputStream stream=context.getContentResolver().openInputStream(uri)){
                    if(stream==null)continue;
                    BitmapFactory.Options options=new BitmapFactory.Options();
                    options.inJustDecodeBounds=true;
                    BitmapFactory.decodeStream(stream,null,options);
                    options.inSampleSize=Math.max(1,Math.max(options.outWidth/1200,options.outHeight/1200));
                    // Reopen input; decodeStream after bounds cannot be reused.
                    try(InputStream stream2=context.getContentResolver().openInputStream(uri)){
                        options.inJustDecodeBounds=false;
                        Bitmap bitmap=BitmapFactory.decodeStream(stream2,null,options);
                        if(bitmap!=null)images.put(cue.uri,bitmap);
                    }
                    }
                }catch(Exception ignored){}
            }
            if(images.containsKey(cue.uri)||gifs.containsKey(cue.uri))cues.add(cue);
        }
        settings=new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f,0.19f)
                .setOverlayFrameAnchor(0f,0f).build();
    }
    @Override public OverlaySettings getOverlaySettings(long timeUs){return settings;}
    @Override public Bitmap getBitmap(long timeUs) {
        long now=trimStartMs+timeUs/1000;
        MediaCue active=null;
        for(MediaCue cue:cues)if(cue.contains(now)){active=cue;break;}
        long age=active==null?0:now-active.startMs;
        long remaining=active==null?0:active.endMs-now;
        long step=active!=null&&(age<260||remaining<180||gifs.containsKey(active.uri))?timeUs/33333:-1;
        if(output==null||prior!=active||step!=priorStep) {
            if(output==null)output=Bitmap.createBitmap(700,545,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(output);
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            if(active!=null){
                Bitmap source=images.get(active.uri);
                Movie gif=gifs.get(active.uri);
                if(source!=null||gif!=null){
                    float alpha=Math.min(1f,Math.min(age/180f,remaining/180f));
                    alpha=Math.max(0f,alpha);
                    float scale=active.effect==1?0.84f+0.16f*Math.min(1f,age/240f):1f;
                    canvas.save();
                    canvas.scale(scale,scale,350f,270f);
                    Paint card=new Paint(Paint.ANTI_ALIAS_FLAG);
                    card.setColor(Color.argb(Math.round(alpha*230),7,15,33));
                    RectF backdrop=new RectF(18,16,682,530);
                    canvas.drawRoundRect(backdrop,26,26,card);
                    RectF bounds=new RectF(28,26,672,520);
                    canvas.save();
                    Path clip=new Path();clip.addRoundRect(bounds,19,19,Path.Direction.CW);
                    canvas.clipPath(clip);
                    float bw=bounds.width(),bh=bounds.height();
                    if(gif!=null){
                        Rect targetBounds=new Rect(28,26,672,520);
                        int layer=canvas.saveLayerAlpha(bounds,Math.max(0,Math.min(255,Math.round(alpha*255))));
                        GifSupport.paint(canvas,gif,targetBounds,(int)age,Math.round(alpha*255));
                        canvas.restoreToCount(layer);
                    }else{
                        float ratio=Math.min(bw/source.getWidth(),bh/source.getHeight());
                        float width=source.getWidth()*ratio,height=source.getHeight()*ratio;
                        RectF target=new RectF(350-width/2,273-height/2,350+width/2,273+height/2);
                        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                        p.setAlpha(Math.round(alpha*255));
                        canvas.drawBitmap(source,null,target,p);
                    }
                    canvas.restore();
                    canvas.restore();
                }
            }
            prior=active;priorStep=step;
        }
        return output;
    }
    @Override public void release()throws androidx.media3.common.VideoFrameProcessingException{
        super.release();
        for(Bitmap b:images.values())if(b!=null&&!b.isRecycled())b.recycle();
        images.clear();
        gifs.clear();
        if(output!=null&&!output.isRecycled())output.recycle();
        output=null;
    }
}
