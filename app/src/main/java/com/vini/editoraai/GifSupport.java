package com.vini.editoraai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.webkit.MimeTypeMap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** GIFs são decodificados sob demanda, até 12 MB, sem instalar pacotes de memes. */
public final class GifSupport {
    private GifSupport() { }
    public static boolean isGif(Context context, Uri uri) {
        String name=uri.toString().toLowerCase(java.util.Locale.ROOT);
        if(name.endsWith(".gif") || name.contains(".gif?")) return true;
        try {
            String mime=context.getContentResolver().getType(uri);
            if("image/gif".equalsIgnoreCase(mime))return true;
            try(InputStream input=context.getContentResolver().openInputStream(uri)) {
                if(input==null)return false;
                byte[] magic=new byte[6];
                return input.read(magic)==6 && magic[0]=='G' && magic[1]=='I'
                     && magic[2]=='F' && magic[3]=='8';
            }
        } catch(Exception e) { return false; }
    }
    public static Movie load(Context context,Uri uri)throws Exception {
        try(InputStream in=context.getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if(in==null)throw new Exception("GIF não pode ser aberto. Confira a conexão com a nuvem.");
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1) {
                if(bytes.size()+n>12*1024*1024)
                    throw new Exception("GIF acima do limite de 12 MB. Use um GIF menor.");
                bytes.write(buf,0,n);
            }
            byte[] data=bytes.toByteArray();
            Movie movie=Movie.decodeByteArray(data,0,data.length);
            if(movie==null || movie.width()<1 || movie.height()<1)
                throw new Exception("O arquivo não é um GIF válido.");
            return movie;
        }
    }
    public static void paint(Canvas canvas, Movie gif, Rect bounds, int timeMs, int alpha) {
        if(gif==null||bounds.width()<=0||bounds.height()<=0)return;
        int duration=gif.duration()>0?gif.duration():1000;
        gif.setTime(Math.floorMod(timeMs,duration));
        float scale=Math.min(bounds.width()/(float)gif.width(),bounds.height()/(float)gif.height());
        canvas.save();
        canvas.translate(bounds.centerX()-gif.width()*scale/2f,
                         bounds.centerY()-gif.height()*scale/2f);
        canvas.scale(scale,scale);
        // Movie does not provide universal per-frame alpha. The enclosing Canvas layer does.
        gif.draw(canvas,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.restore();
    }
    /** Prévia animada real do GIF, independente do player de vídeo. */
    public static final class DrawableGif extends Drawable implements Runnable {
        private final Movie movie;
        private long begun=SystemClock.uptimeMillis();
        private boolean running=true;
        public DrawableGif(Movie movie){this.movie=movie;}
        @Override public void draw(Canvas canvas){
            Rect bounds=getBounds();
            paint(canvas,movie,bounds,(int)(SystemClock.uptimeMillis()-begun),255);
            if(running)scheduleSelf(this,SystemClock.uptimeMillis()+55);
        }
        @Override public void run(){if(running)invalidateSelf();}
        public void stop(){running=false;unscheduleSelf(this);}
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
