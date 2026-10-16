package com.vini.editoraai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;

import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.StaticOverlaySettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Efeitos de ação ANIME desenhados sobre canal alfa transparente.
 * Nenhum modo desenha fundo opaco/azul; o vídeo continua visível por baixo.
 * Os triângulos brancos são partículas reais animadas, não um filtro sólido.
 */
@UnstableApi
public final class VisualFxOverlay extends BitmapOverlay {
    public static final String[] MODES = {
            "Sem efeitos", "✧ Partículas BRANCAS triangulares (anime)",
            "⚡ Linhas brancas de ação", "✦ Impacto com triângulos",
            "✧ Flash branco suave"
    };
    private static final int W=720, H=1280;
    private final int mode;
    private final long durationMs;
    private final List<Long> moments=new ArrayList<>();
    private final OverlaySettings settings=new StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f,0f)
            .setOverlayFrameAnchor(0f,0f).build();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap buffer;
    private long lastFrame=-1;

    public VisualFxOverlay(int selected,long durationMs,List<Long> joins){
        mode=Math.max(0,Math.min(MODES.length-1,selected));
        this.durationMs=durationMs;
        moments.add(0L);
        if(joins!=null&&!joins.isEmpty()){
            for(Long join:joins) if(join!=null&&join>250&&join<durationMs)moments.add(join);
        }else {
            for(long ms=8000;ms<durationMs;ms+=8000)moments.add(ms);
        }
        Collections.sort(moments);
    }
    @Override public OverlaySettings getOverlaySettings(long tUs){return settings;}

    @Override public Bitmap getBitmap(long timeUs){
        long timeMs=Math.max(0,timeUs/1000L);
        // Limita a renderização a ~30 fps e preserva a animação ao exportar.
        long frameId=timeMs/33L;
        if(buffer==null)buffer=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        if(frameId==lastFrame)return buffer;
        lastFrame=frameId;
        Canvas canvas=new Canvas(buffer);
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        if(mode==0||timeMs>=durationMs)return buffer;
        long age=Long.MAX_VALUE;
        int eventIndex=0;
        for(int i=0;i<moments.size();i++){
            long delta=timeMs-moments.get(i);
            if(delta>=0&&delta<age){age=delta;eventIndex=i;}
        }
        if(age>=620)return buffer;
        float phase=age/620f;
        float fade=(float)Math.pow(1-phase,1.4);
        if(mode==1||mode==3){
            drawShards(canvas,phase,fade,eventIndex,mode==3?30:18);
            if(mode==3)drawImpact(canvas,phase,fade);
        }else if(mode==2){
            drawLines(canvas,phase,fade,eventIndex);
        }else if(mode==4){
            // Sempre transparente, inclusive no pico (não cobre totalmente o rosto).
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(fade*38f),255,255,255));
            canvas.drawRect(0,0,W,H,paint);
            drawShards(canvas,phase,fade,eventIndex,9);
        }
        return buffer;
    }

    private void drawShards(Canvas canvas,float phase,float fade,int eventIndex,int count){
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<count;i++){
            // Distribuição pseudoaleatória determinística, sem piscar de lugar a cada frame.
            double seed=i*94.27+eventIndex*16.89;
            float startX=(float)(((Math.sin(seed*4.1)+1)*0.5)*W);
            float startY=(float)(((Math.sin(seed*2.77)+1)*0.5)*H);
            float vx=(float)(Math.cos(seed)* (180+i%5*85));
            float vy=(float)(Math.sin(seed*0.61)* (230+i%4*110));
            float x=startX+vx*phase;
            float y=startY+vy*phase;
            float edge=9+(i%5)*5;
            float rot=(float)(seed+phase*(1.8+(i%4)));
            paint.setColor(Color.argb(Math.min(255,Math.round(fade*(140+(i%5)*20))),
                    255,255,255));
            triangle(canvas,x,y,edge,rot);
            // Segundo fragmento cria sensação de ação sem preencher o plano de fundo.
            if(i%4==0){
                paint.setColor(Color.argb(Math.round(fade*95f),255,255,255));
                triangle(canvas,x-vx*0.10f,y-vy*0.10f,edge*0.55f,rot+1.6f);
            }
        }
    }
    private void triangle(Canvas canvas,float x,float y,float radius,float angle){
        Path path=new Path();
        for(int i=0;i<3;i++){
            double a=angle+Math.PI*2*i/3;
            float tx=x+(float)Math.cos(a)*radius;
            float ty=y+(float)Math.sin(a)*radius;
            if(i==0)path.moveTo(tx,ty);else path.lineTo(tx,ty);
        }
        path.close();canvas.drawPath(path,paint);
    }
    private void drawLines(Canvas canvas,float phase,float fade,int event){
        paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<18;i++){
            double a=i*Math.PI*2/18.0+event*0.1;
            float cx=W/2f+(float)Math.cos(a)* (80+phase*125);
            float cy=H/2f+(float)Math.sin(a)* (90+phase*140);
            float radius=130+phase*350+(i%3)*40;
            paint.setStrokeWidth(2f+(i%3)*2f);
            paint.setColor(Color.argb(Math.round((110+(i%4)*25)*fade),255,255,255));
            canvas.drawLine(cx,cy,cx+(float)Math.cos(a)*radius,
                    cy+(float)Math.sin(a)*radius,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private void drawImpact(Canvas canvas,float phase,float fade){
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9f);
        paint.setColor(Color.argb(Math.round(175*fade),255,255,255));
        canvas.drawCircle(W/2f,H/2f,70+phase*360,paint);
        paint.setStrokeWidth(3f);
        canvas.drawCircle(W/2f,H/2f,95+phase*400,paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public void release()throws VideoFrameProcessingException{
        super.release();
        if(buffer!=null&&!buffer.isRecycled())buffer.recycle();
        buffer=null;
    }
}
