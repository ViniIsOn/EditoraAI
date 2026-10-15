package com.vini.editoraai;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Estilos de legenda compartilhados pela prévia e pelo MP4 exportado. */
public final class CaptionStyle {
    public static final String[] STYLES = {"Clássico • branco", "Neon • ciano", "Destaque • amarelo", "Clean • cartão"};
    public static final String[] ANIMATIONS = {"Sem animação", "Pop • entrada", "Fade • entrada"};
    private static final int CYAN = Color.rgb(68, 240, 241);
    private static final int YELLOW = Color.rgb(255, 216, 57);
    private CaptionStyle() {}

    public static int safeStyle(int style) { return Math.max(0, Math.min(STYLES.length - 1, style)); }
    public static int safeAnimation(int animation) { return Math.max(0, Math.min(ANIMATIONS.length - 1, animation)); }

    private static float progress(long ageMs) {
        return Math.max(0f, Math.min(1f, ageMs / 190f));
    }
    public static float scale(int animation, long ageMs) {
        return animation == 1 ? (0.83f + 0.17f * progress(ageMs)) : 1f;
    }
    public static float opacity(int animation, long ageMs) {
        return animation == 2 ? progress(ageMs) : 1f;
    }
    private static GradientDrawable rounded(int fill, int stroke, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (stroke != Color.TRANSPARENT) d.setStroke(2, stroke);
        return d;
    }
    public static void preview(TextView view, int rawStyle, int rawAnimation, long ageMs, boolean visible) {
        int style = safeStyle(rawStyle), animation = safeAnimation(rawAnimation);
        view.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        if (!visible) return;
        view.setAlpha(opacity(animation, ageMs));
        view.setScaleX(scale(animation, ageMs));
        view.setScaleY(scale(animation, ageMs));
        view.setTextSize(style == 3 ? 18f : 21f);
        view.setTypeface(Typeface.create(style == 3 ? "sans-serif-medium" : "sans-serif-condensed",
                style == 3 ? Typeface.NORMAL : Typeface.BOLD));
        view.setAllCaps(style != 3);
        view.setPadding(10, 10, 10, 10);
        if (style == 1) {
            view.setTextColor(CYAN);
            view.setShadowLayer(6f, 1f, 1f, Color.BLACK);
            view.setBackground(rounded(Color.argb(215, 8, 21, 40), Color.argb(210, 29, 184, 210), 16));
        } else if (style == 2) {
            view.setTextColor(YELLOW);
            view.setShadowLayer(6f, 2f, 2f, Color.BLACK);
            view.setBackground(null);
        } else if (style == 3) {
            view.setTextColor(Color.rgb(18, 25, 36));
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            view.setBackground(rounded(Color.argb(245, 255, 255, 255), Color.TRANSPARENT, 15));
        } else {
            view.setTextColor(Color.WHITE);
            view.setShadowLayer(6f, 2f, 2f, Color.BLACK);
            view.setBackground(null);
        }
    }

    public static void draw(Canvas canvas, String rawText, int rawStyle, int rawAnimation, long ageMs) {
        int style = safeStyle(rawStyle), animation = safeAnimation(rawAnimation);
        String content = rawText == null ? "" : rawText.replace('\n',' ').trim();
        if (content.isEmpty()) return;
        if (style != 3) content = content.toUpperCase(Locale.forLanguageTag("pt-BR"));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setTypeface(Typeface.create(style == 3 ? "sans-serif-medium" : "sans-serif-condensed",
                style == 3 ? Typeface.NORMAL : Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        float font = style == 3 ? 46 : 51;
        List<String> lines;
        do {
            paint.setTextSize(font);
            lines = wrap(content, paint, 650);
            if (lines.size() <= 3 || font <= 32) break;
            font -= 3;
        } while (true);
        // Nunca desenhar texto fora da imagem, mesmo para frases longas.
        if (lines.size() > 4) {
            StringBuilder last = new StringBuilder(lines.get(3));
            if (lines.size() > 4) last.append("…");
            lines = new ArrayList<>(lines.subList(0, 4));
            lines.set(3, last.toString());
        }
        final float lineH = font * 1.18f;
        final float startY = 122f - (lines.size() - 1) * lineH / 2f;
        final int opacity = Math.round(opacity(animation, ageMs) * 255);
        if (opacity <= 0) return;
        canvas.save();
        float zoom = scale(animation, ageMs);
        canvas.scale(zoom, zoom, 350f, 120f);
        float maxLine = 0;
        for (String line : lines) maxLine = Math.max(maxLine, paint.measureText(line));
        if (style == 1 || style == 3) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(style == 1 ? Color.rgb(9, 23, 40) : Color.WHITE);
            bg.setAlpha(Math.round(opacity * (style == 1 ? 0.86f : 0.96f)));
            float top = startY - font - 14, bottom = startY + (lines.size()-1) * lineH + 19;
            canvas.drawRoundRect(new RectF(350 - maxLine/2 - 23, top,
                    350 + maxLine/2 + 23, bottom), 22, 22, bg);
        }
        float y = startY;
        for (String line : lines) {
            if (style != 3) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(style == 1 ? 6f : 9f);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(style == 1 ? Color.rgb(5, 28, 45) : Color.BLACK);
                paint.setAlpha(opacity);
                canvas.drawText(line, 350, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style == 1 ? CYAN : style == 2 ? YELLOW :
                    style == 3 ? Color.rgb(18,25,36) : Color.WHITE);
            paint.setAlpha(opacity);
            canvas.drawText(line, 350, y, paint);
            y += lineH;
        }
        canvas.restore();
    }

    private static List<String> wrap(String text, Paint paint, float width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split("\\s+")) {
            String attempt = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && paint.measureText(attempt) > width) {
                lines.add(line);
                line = word;
            } else line = attempt;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }
}
