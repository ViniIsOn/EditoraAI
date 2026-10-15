package com.vini.editoraai;

import android.content.Context;
import java.io.File;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared typography and animation engine for the editor preview and rendered MP4. */
public final class CaptionStyle {
    public static final String[] STYLES = {
        "Clássico • borda preta", "Neon • ciano", "Destaque • amarelo", "Clean • cartão branco",
        "Glow • rosa", "Cinema • sombreado", "Black pill • branco", "Sunset • degradê"
    };
    public static final String[] FONTS = {
        "Impacto • Black", "Moderna • Sans", "Condensada • Bold", "Arredondada • Rounded",
        "Elegante • Serif", "Máquina • Mono", "Escrita • Casual", "Manuscrita • Cursive",
        "Fina • Light", "Intermediária • Medium", "Tradicional • Serif Bold", "Pequenas caps • Small Caps", "Minha fonte .TTF/.OTF"
    };
    private static final String[] FAMILIES = {
        "sans-serif-black", "sans-serif", "sans-serif-condensed", "sans-serif-rounded",
        "serif", "monospace", "casual", "cursive", "sans-serif-light",
        "sans-serif-medium", "serif", "sans-serif-smallcaps"
    };
    public static final String[] ANIMATIONS = {
        "Sem animação", "Pop • elástico", "Fade • suave", "Subir • deslizar", "Entrar pela esquerda",
        "Entrar pela direita", "Bounce • quicar", "Zoom • crescer", "Girar • rotação",
        "Digitação • letra a letra", "Pulse • pulsar", "Cair • de cima"
    };
    public static final String[] POSITIONS = {"Embaixo", "Centro", "Em cima"};
    public static final int CUSTOM_FONT_INDEX = FONTS.length-1;
    public static File customFontFile(Context context){return new File(context.getFilesDir(),"editora_custom_font.ttf");}
    private static final int CYAN = Color.rgb(68, 240, 241);
    private static final int YELLOW = Color.rgb(255, 219, 60);
    private static final int PINK = Color.rgb(255, 126, 216);
    private CaptionStyle() {}

    public static int safeStyle(int n) { return clamp(n, 0, STYLES.length - 1); }
    public static int safeFont(int n) { return clamp(n, 0, FONTS.length - 1); }
    public static int safeAnimation(int n) { return clamp(n, 0, ANIMATIONS.length - 1); }
    public static int safePosition(int n) { return clamp(n, 0, POSITIONS.length - 1); }
    public static int safeSize(int n) { return clamp(n, 70, 145); }
    private static int clamp(int n, int lo, int hi) { return Math.max(lo, Math.min(hi, n)); }
    private static float within(float n) { return Math.max(0f, Math.min(1f, n)); }
    private static float t(long ageMs, int durationMs) { return within(ageMs / (float) durationMs); }
    private static float easeOut(float v) { return 1f - (1f-v)*(1f-v)*(1f-v); }
    private static float bump(float v) {
        if (v >= 1f) return 1f;
        return 1f + (float)(Math.pow(2, -9.5f*v) * Math.sin((v*11f-2f)*Math.PI/3f))*0.24f;
    }
    private static float bounce(float v) {
        if (v>=1f) return 1f;
        return 1f - (float) Math.pow(1-v, 2.1) * (float) Math.abs(Math.cos(v*3.5*Math.PI));
    }
    public static float opacity(int raw, long age) {
        int a = safeAnimation(raw);
        if (a==2 || a==7) return t(age, 290);
        return 1f;
    }
    public static float scale(int raw, long age) {
        int a = safeAnimation(raw);
        if (a==1) return 0.74f + 0.26f*bump(t(age, 340));
        if (a==6) return 0.50f + 0.50f*bounce(t(age, 510));
        if (a==7) return 0.55f + 0.45f*easeOut(t(age, 380));
        if (a==10) return 1f + 0.045f*(float)Math.sin((age / 150f) * Math.PI);
        return 1f;
    }
    public static float dx(int raw,long age) {
        int a = safeAnimation(raw);
        if (a==4) return -110f*(1f-easeOut(t(age, 320)));
        if (a==5) return 110f*(1f-easeOut(t(age, 320)));
        return 0f;
    }
    public static float dy(int raw,long age) {
        int a=safeAnimation(raw);
        if (a==3) return 72f*(1f-easeOut(t(age, 330)));
        if (a==11) return -87f*(1f-easeOut(t(age, 330)));
        if (a==6) return -24f*(1f-bounce(t(age, 510)));
        return 0f;
    }
    public static float rotation(int raw,long age) {
        return safeAnimation(raw)==8 ? -16f*(1f-easeOut(t(age, 390))) : 0f;
    }
    public static String shownText(String original, int animation, long age) {
        if (original==null) return "";
        if (safeAnimation(animation) != 9) return original;
        int count = Math.max(1, Math.min(original.length(), (int)Math.ceil(original.length()*t(age, 680))));
        // Never cut a surrogate pair between emoji/code points.
        if (count < original.length() && Character.isHighSurrogate(original.charAt(count-1))) count--;
        return original.substring(0, Math.max(0,count));
    }
    public static boolean continuous(int raw,long age) {
        int a=safeAnimation(raw);
        return a==10 || (a!=0 && age<=760);
    }
    public static Typeface font(int raw) {
        int idx=safeFont(raw);
        if(idx>=FAMILIES.length) idx=0;
        int weight=(idx==7 || idx==8 || idx==9) ? Typeface.NORMAL : Typeface.BOLD;
        return Typeface.create(FAMILIES[idx], weight);
    }
    public static Typeface font(Context context,int raw){
        if(safeFont(raw)==CUSTOM_FONT_INDEX) {
            try {
                File file=customFontFile(context);
                if(file.isFile()) return Typeface.createFromFile(file);
            } catch(Exception ignored){}
        }
        return font(raw);
    }
    public static int textColor(int raw) {
        switch (safeStyle(raw)) {
            case 1: return CYAN;
            case 2: return YELLOW;
            case 3: return Color.rgb(18,29,41);
            case 4: return PINK;
            case 7: return Color.rgb(255,221,107);
            default: return Color.WHITE;
        }
    }
    private static GradientDrawable rounded(int fill, int stroke, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(radiusPx);
        if (stroke!=Color.TRANSPARENT) d.setStroke(2,stroke);
        return d;
    }

    /** Preview text transformations mirror the ones used in Canvas at export time. */
    public static void preview(TextView view, int rawStyle, int rawAnimation,
                               int rawFont,int rawSize,int rawPosition,long ageMs, boolean visible) {
        int style=safeStyle(rawStyle), anim=safeAnimation(rawAnimation);
        view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        if (!visible) return;
        view.setAlpha(opacity(anim,ageMs));
        float value=scale(anim,ageMs);view.setScaleX(value);view.setScaleY(value);
        float density=view.getResources().getDisplayMetrics().density;
        view.setTranslationX(dx(anim,ageMs)*density*.48f);
        view.setTranslationY(dy(anim,ageMs)*density*.48f);
        view.setRotation(rotation(anim,ageMs));
        view.setTextSize((style==3 ? 18f : 22f)*safeSize(rawSize)/100f);
        view.setTypeface(font(view.getContext(),rawFont));
        view.setAllCaps(style!=3 && style!=5);
        view.setGravity(Gravity.CENTER);
        view.setPadding((int)(13*density),(int)(9*density),(int)(13*density),(int)(9*density));
        view.setTextColor(textColor(style));
        if (style==1) {
            view.setBackground(rounded(Color.argb(218,8,19,32),Color.argb(230,43,217,238),(int)(14*density)));
            view.setShadowLayer(2f,1f,1f,Color.BLACK);
        } else if (style==3) {
            view.setBackground(rounded(Color.argb(247,255,255,255),Color.TRANSPARENT,(int)(14*density)));
            view.setShadowLayer(0,0,0,Color.TRANSPARENT);
        } else if (style==6) {
            view.setBackground(rounded(Color.argb(235,8,8,12),Color.TRANSPARENT,(int)(21*density)));
            view.setShadowLayer(1f,1f,1f,Color.BLACK);
        } else {
            view.setBackground(null);
            view.setShadowLayer(style==4?11f:6f,1f,2f,style==4?PINK:Color.BLACK);
        }
        if (view.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)view.getLayoutParams();
            int position=safePosition(rawPosition);
            int gravity=position==1?Gravity.CENTER:position==2?Gravity.TOP:Gravity.BOTTOM;
            int bottom=(int)((position==0?60:0)*density);
            int top=(int)((position==2?80:0)*density);
            if (p.gravity!=gravity || p.bottomMargin!=bottom || p.topMargin!=top) {
                p.gravity=gravity;p.bottomMargin=bottom;p.topMargin=top;
                view.setLayoutParams(p);
            }
        }
    }

    /** Exact typography and time-driven animation burned into the exported MP4. */
    public static void draw(Canvas canvas,String rawText,int rawStyle,int rawAnimation,
                            int rawFont,int rawSize,long ageMs,Typeface resolvedFont) {
        int style=safeStyle(rawStyle), anim=safeAnimation(rawAnimation);
        String content=shownText(rawText,anim,ageMs).replace('\n',' ').trim();
        if (content.isEmpty()) return;
        if (style!=3 && style!=5) content=content.toUpperCase(Locale.forLanguageTag("pt-BR"));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(resolvedFont==null?font(rawFont):resolvedFont);
        paint.setTextAlign(Paint.Align.CENTER);
        float f=(style==3?45f:54f)*safeSize(rawSize)/100f;
        List<String> lines;
        do {
            paint.setTextSize(f);
            lines=wrap(content,paint,635f);
            if (lines.size()<=3 || f<=29f) break;
            f-=2.8f;
        } while (true);
        if (lines.size()>4) {
            lines=new ArrayList<>(lines.subList(0,4));
            String last=lines.get(3);
            lines.set(3,last.length()>2?last.substring(0,last.length()-1)+"…":"…");
        }
        float lineH=f*1.2f;
        float startY=132f-(lines.size()-1)*lineH/2f;
        int alpha=Math.round(opacity(anim,ageMs)*255);
        if(alpha<=0) return;
        float longest=0f;
        for(String line:lines) longest=Math.max(longest,paint.measureText(line));
        canvas.save();
        canvas.translate(dx(anim,ageMs),dy(anim,ageMs));
        canvas.rotate(rotation(anim,ageMs),350f,133f);
        float zoom=scale(anim,ageMs);
        canvas.scale(zoom,zoom,350f,133f);
        if(style==1 || style==3 || style==6) {
            Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(style==3?Color.WHITE:style==1?Color.rgb(10,26,40):Color.rgb(8,8,12));
            bg.setAlpha(Math.round(alpha*(style==3?.97f:.90f)));
            canvas.drawRoundRect(new RectF(350-longest/2-22,startY-f-14,
                    350+longest/2+22,startY+(lines.size()-1)*lineH+19),
                    style==6?35f:18f,style==6?35f:18f,bg);
        }
        float y=startY;
        for(String line:lines) {
            if(style!=3 && style!=6) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(style==5?5f:style==4?6f:9.2f);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(style==1?Color.rgb(3,19,37):Color.BLACK);
                paint.setAlpha(alpha);
                canvas.drawText(line,350,y,paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(textColor(style));
            paint.setAlpha(alpha);
            if(style==7) {
                paint.setShader(new LinearGradient(0,y-f,0,y+6f,
                    Color.rgb(255,233,100),Color.rgb(255,103,141),Shader.TileMode.CLAMP));
            }
            if(style==4) paint.setShadowLayer(7f,0f,0f,PINK);
            if(style==5) paint.setShadowLayer(4f,1f,2f,Color.BLACK);
            canvas.drawText(line,350,y,paint);
            paint.clearShadowLayer();
            paint.setShader(null);
            y+=lineH;
        }
        canvas.restore();
    }
    private static List<String> wrap(String text,Paint paint,float width) {
        List<String> lines=new ArrayList<>();
        String line="";
        for(String word:text.split("\\s+")) {
            String candidate=line.isEmpty()?word:line+" "+word;
            if(!line.isEmpty() && paint.measureText(candidate)>width) {
                lines.add(line);line=word;
            } else line=candidate;
        }
        if(!line.isEmpty())lines.add(line);
        return lines;
    }
}
