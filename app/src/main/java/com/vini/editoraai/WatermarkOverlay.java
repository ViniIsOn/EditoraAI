package com.vini.editoraai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.net.Uri;

import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.StaticOverlaySettings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Logotipo selecionado pelo usuário com canal alfa, incorporado a todos os frames exportados. */
@UnstableApi
public final class WatermarkOverlay extends BitmapOverlay {
    public static final String NAME="editoraai_watermark.png";
    public static final int TOP_LEFT=0, TOP_RIGHT=1, BOTTOM_LEFT=2, BOTTOM_RIGHT=3;
    private static final int MAX_BYTES=8*1024*1024;
    private static final int OUT_W=720, OUT_H=1280;
    private final OverlaySettings settings=new StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f,0f)
            .setOverlayFrameAnchor(0f,0f).build();
    private Bitmap canvasBitmap;

    public static File asset(Context context){return new File(context.getFilesDir(),NAME);}
    public static boolean hasAsset(Context context){return asset(context).isFile()&&asset(context).length()>0;}

    /** Importa uma única imagem aprovada, normaliza como PNG privado e preserva transparência. */
    public static void install(Context context,Uri uri) throws Exception {
        ByteArrayOutputStream memory=new ByteArrayOutputStream();
        try(InputStream stream=context.getContentResolver().openInputStream(uri)) {
            if(stream==null)throw new Exception("Imagem indisponível.");
            byte[] buffer=new byte[8192];
            int count,total=0;
            while((count=stream.read(buffer))!=-1) {
                total+=count;
                if(total>MAX_BYTES)throw new Exception("Escolha imagem menor que 8 MB.");
                memory.write(buffer,0,count);
            }
        }
        byte[] raw=memory.toByteArray();
        BitmapFactory.Options dimensions=new BitmapFactory.Options();
        dimensions.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(raw,0,raw.length,dimensions);
        if(dimensions.outWidth<=0||dimensions.outHeight<=0
                ||dimensions.outWidth>10000||dimensions.outHeight>10000
                ||(long)dimensions.outWidth*dimensions.outHeight>24000000L)
            throw new Exception("Use um PNG, JPG ou WebP válido, com até 24 milhões de pixels.");
        BitmapFactory.Options options=new BitmapFactory.Options();
        int maxDim=Math.max(dimensions.outWidth,dimensions.outHeight);
        options.inSampleSize=1;
        while(maxDim/options.inSampleSize>1500)options.inSampleSize*=2;
        Bitmap image=BitmapFactory.decodeByteArray(raw,0,raw.length,options);
        if(image==null)throw new Exception("O formato da imagem não é compatível.");
        File tmp=new File(context.getFilesDir(),NAME+".tmp");
        File destination=asset(context);
        try(FileOutputStream output=new FileOutputStream(tmp)) {
            if(!image.compress(Bitmap.CompressFormat.PNG,100,output))
                throw new Exception("Não consegui converter imagem para PNG.");
            output.flush();
        } finally {
            image.recycle();
        }
        if(tmp.length()==0){tmp.delete();throw new Exception("Não foi possível salvar o PNG.");}
        if(destination.exists()&&!destination.delete()){
            tmp.delete();throw new Exception("Não consegui substituir o logo anterior.");
        }
        if(!tmp.renameTo(destination)){
            tmp.delete();throw new Exception("Não consegui guardar o logo.");
        }
    }

    /** Largura porcentual em relação ao vídeo (720px), com margem de segurança. */
    public static RectF position(int bitmapW,int bitmapH,int size,int corner){
        int clamped=Math.max(12,Math.min(45,size));
        float width=Math.max(20f,OUT_W*clamped/100f);
        float height=width*bitmapH/Math.max(1f,bitmapW);
        if(height>OUT_H*0.32f){
            height=OUT_H*0.32f;
            width=height*bitmapW/Math.max(1f,bitmapH);
        }
        float edgeX=32f,edgeY=48f;
        float left=(corner==TOP_RIGHT||corner==BOTTOM_RIGHT)
                ?OUT_W-edgeX-width:edgeX;
        float top=(corner==BOTTOM_LEFT||corner==BOTTOM_RIGHT)
                ?OUT_H-edgeY-height:edgeY;
        return new RectF(left,top,left+width,top+height);
    }

    public WatermarkOverlay(Context context,int corner,int size,int alpha)throws Exception {
        File file=asset(context);
        if(!file.isFile())throw new Exception("Importe o logo antes de exportar.");
        Bitmap logo=BitmapFactory.decodeFile(file.getAbsolutePath());
        if(logo==null)throw new Exception("O logo não pôde ser aberto.");
        try{
            canvasBitmap=Bitmap.createBitmap(OUT_W,OUT_H,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(canvasBitmap);
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
            ink.setAlpha(Math.round(255f*Math.max(0,Math.min(100,alpha))/100f));
            canvas.drawBitmap(logo,null,position(logo.getWidth(),logo.getHeight(),size,corner),ink);
        }finally{logo.recycle();}
    }

    @Override public OverlaySettings getOverlaySettings(long presentationTimeUs){return settings;}
    @Override public Bitmap getBitmap(long presentationTimeUs){return canvasBitmap;}
    @Override public void release()throws VideoFrameProcessingException{
        super.release();
        if(canvasBitmap!=null&&!canvasBitmap.isRecycled())canvasBitmap.recycle();
        canvasBitmap=null;
    }
}
