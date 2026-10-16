package com.vini.editoraai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;

import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.StaticOverlaySettings;

/**
 * Transições gráficas no começo de um clipe e acentos a cada ~8s.
 * São sobreposições animadas, não crossfade entre dois vídeos.
 */
@UnstableApi
public final class VisualFxOverlay extends BitmapOverlay {
    public static final String[] MODES={"Nenhum","Flash anime","Linhas de velocidade","Glitch colorido","Explosão pop"};
    private static final int W=720,H=1280;
    private final int mode;
    private final long durationMs;
    private final boolean transition;
    private final OverlaySettings settings=new StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f,0f)
            .setOverlayFrameAnchor(0f,0f).build();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap frame;
    private long lastStep=Long.MIN_VALUE;

    public VisualFxOverlay(int mode,long durationMs,boolean transition){
        this.mode=Math.max(0,Math.min(MODES.length-1,mode));
        this.durationMs=durationMs;
        this.transition=transition;
    }
    @Override public OverlaySettings getOverlaySettings(long timeUs){return settings;}
    @Override public Bitmap getBitmap(long timeUs){
        long timeMs=timeUs/1000L;
        long step=timeMs/33333;
        if(frame!=null&&lastStep==step)return frame;
        if(frame==null)frame=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        lastStep=step;
        Canvas canvas=new Canvas(frame);
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        if(mode==0||timeMs>durationMs)return frame;
        long phase;
        if(transition && timeMs<470)phase=timeMs;
        else if(timeMs>=8000 && (timeMs-8000)%9000<450)
            phase=(timeMs-8000)%9000;
        else return frame;
        float fade=Math.max(0f,1f-phase/465f);
        float intensity=fade*fade;
        if(mode==1){
            paint.setColor(Color.argb(Math.round(185*intensity),255,255,255));
            canvas.drawRect(0,0,W,H,paint);
            paint.setColor(Color.argb(Math.round(95*intensity),67,235,255));
            canvas.drawRect(0,H*0.64f,W,H*0.68f,paint);
        }else if(mode==2){
            for(int j=0;j<26;j++){
                float y=j*53f+((phase*1.8f)%55f);
                float thickness=3f+(j%4)*2;
                paint.setStrokeWidth(thickness);
                paint.setColor(Color.argb(Math.round((42+(j%5)*15)*intensity),255,255,255));
                canvas.drawLine(j%2==0?-40:W+40,y,j%2==0?W*0.82f:W*0.18f,H*0.51f,paint);
            }
        }else if(mode==3){
            for(int j=0;j<11;j++){
                float y=((j*139)+(phase*5)%H)%H;
                float width=125+(j%6)*94;
                paint.setColor(Color.argb(Math.round(95*intensity),j%2==0?25:255,220,j%2==0?255:85));
                canvas.drawRect((j*93)%W,y,Math.min(W,(j*93)%W+width),y+5+(j%3)*5,paint);
            }
        }else if(mode==4){
            paint.setColor(Color.argb(Math.round(80*intensity),255,230,35));
            canvas.drawCircle(W/2f,H/2f,150+phase/3f,paint);
            paint.setColor(Color.argb(Math.round(190*intensity),255,255,255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(13f);
            canvas.drawCircle(W/2f,H/2f,100+phase*0.8f,paint);
            paint.setStyle(Paint.Style.FILL);
            for(int j=0;j<18;j++){
                double a=j*Math.PI/9;
                float x=W/2f+(float)Math.cos(a)*(130+phase*1.1f);
                float y=H/2f+(float)Math.sin(a)*(130+phase*1.1f);
                paint.setColor(Color.argb(Math.round(220*intensity),j%2==0?255:35,220,j%2==0?35:255));
                canvas.drawCircle(x,y,6+(j%3)*3,paint);
            }
        }
        return frame;
    }
    @Override public void release()throws VideoFrameProcessingException{
        super.release();
        if(frame!=null&&!frame.isRecycled())frame.recycle();
        frame=null;
    }
}
