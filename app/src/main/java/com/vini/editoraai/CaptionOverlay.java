package com.vini.editoraai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.StaticOverlaySettings;
import java.util.ArrayList;
import java.util.List;

/** Burn-in renderer using exactly the same font and animation settings as preview. */
@UnstableApi
public final class CaptionOverlay extends BitmapOverlay {
    private final List<Caption> captions;
    private final long trimStartMs;
    private final int style,animation,font,size,position;
    private final StaticOverlaySettings settings;
    private Bitmap bitmap;
    private String lastText=null;
    private long lastStep=Long.MIN_VALUE;
    public CaptionOverlay(List<Caption> captions,long trimStartMs,
                          int style,int animation,int font,int size,int position) {
        this.captions=new ArrayList<>(captions);
        this.trimStartMs=trimStartMs;
        this.style=CaptionStyle.safeStyle(style);
        this.animation=CaptionStyle.safeAnimation(animation);
        this.font=CaptionStyle.safeFont(font);
        this.size=CaptionStyle.safeSize(size);
        this.position=CaptionStyle.safePosition(position);
        // OpenGL origin is at image center, vertical - is below center.
        float y=this.position==0 ? -0.62f : this.position==2 ? 0.62f : 0f;
        this.settings=new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f,y)
                .setOverlayFrameAnchor(0f,0f).build();
    }
    @Override public OverlaySettings getOverlaySettings(long t){return settings;}
    @Override public Bitmap getBitmap(long timestampUs){
        long ms=trimStartMs+timestampUs/1000;
        Caption active=null;
        for(Caption c:captions) if(c.contains(ms)){active=c;break;}
        String s=active==null?"":active.text;
        long age=active==null?0:Math.max(0,ms-active.startMs);
        long step=active==null?-1:CaptionStyle.continuous(animation,age)?timestampUs/33000:-1;
        if(bitmap==null || !s.equals(lastText) || step!=lastStep){
            if(bitmap==null)bitmap=Bitmap.createBitmap(700,266,Bitmap.Config.ARGB_8888);
            lastText=s;lastStep=step;
            Canvas canvas=new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            if(active!=null)CaptionStyle.draw(canvas,s,style,animation,font,size,age);
        }
        return bitmap;
    }
}
