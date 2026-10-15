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

/** Legendas estilizadas e animação gravada quadro a quadro na exportação Media3. */
@UnstableApi
public final class CaptionOverlay extends BitmapOverlay {
    private final List<Caption> captions;
    private final long trimStartMs;
    private final int style, animation;
    private final StaticOverlaySettings settings;
    private Bitmap bitmap;
    private String lastText = null;
    private long lastStep = Long.MIN_VALUE;

    public CaptionOverlay(List<Caption> captions, long trimStartMs, int style, int animation) {
        this.captions = new ArrayList<>(captions);
        this.trimStartMs = trimStartMs;
        this.style = CaptionStyle.safeStyle(style);
        this.animation = CaptionStyle.safeAnimation(animation);
        this.settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -0.63f)
                .setOverlayFrameAnchor(0f, 0f)
                .build();
    }

    @Override public OverlaySettings getOverlaySettings(long timeUs) { return settings; }

    @Override public Bitmap getBitmap(long presentationTimeUs) {
        long ms = trimStartMs + presentationTimeUs / 1000;
        Caption active = null;
        for (Caption c : captions) {
            if (c.contains(ms)) { active = c; break; }
        }
        String selected = active == null ? "" : active.text;
        long ageMs = active == null ? 0L : Math.max(0, ms - active.startMs);
        // Atualiza no máximo ~20 quadros/s nos primeiros 200ms da animação.
        long step = animation == 0 || ageMs >= 220 ? -1L : ageMs / 50L;
        if (bitmap == null || !selected.equals(lastText) || step != lastStep) {
            if (bitmap == null) bitmap = Bitmap.createBitmap(700, 230, Bitmap.Config.ARGB_8888);
            lastText = selected; lastStep = step;
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            if (!selected.isEmpty()) CaptionStyle.draw(canvas, selected, style, animation, ageMs);
        }
        return bitmap;
    }
}
