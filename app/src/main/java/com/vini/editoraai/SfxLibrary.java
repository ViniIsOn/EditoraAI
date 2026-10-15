package com.vini.editoraai;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Efeitos originais sintetizados em WAV; não são áudios copiados de memes. */
public final class SfxLibrary {
    public static final String[] NAMES={"Pop","Whoosh","Vitória","Boing","Erro suave"};
    public static Uri file(Context context,int effect)throws Exception {
        int kind=Math.max(0,Math.min(NAMES.length-1,effect));
        File f=new File(context.getCacheDir(),"editora_sfx_"+kind+".wav");
        if(!f.isFile()||f.length()<100)writeWav(f,kind);
        return Uri.fromFile(f);
    }
    public static int durationMs(int k){return new int[]{220,350,480,500,340}[Math.max(0,Math.min(4,k))];}
    private static void writeWav(File file,int kind)throws Exception{
        final int rate=22050, length=rate*durationMs(kind)/1000, size=length*2;
        Random rand=new Random(814+kind);
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(new byte[]{'R','I','F','F'});
            le(out,36+size,4);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            le(out,16,4);le(out,1,2);le(out,1,2);le(out,rate,4);
            le(out,rate*2,4);le(out,2,2);le(out,16,2);
            out.write(new byte[]{'d','a','t','a'});le(out,size,4);
            for(int i=0;i<length;i++){
                double t=i/(double)rate,span=i/(double)length;
                double decay=Math.pow(1-span,kind==1?1.8:2.6);
                double frequency;
                switch(kind){
                    case 0:frequency=650-460*span;break;
                    case 1:frequency=380+620*span;break;
                    case 2:frequency=600+210*span;break;
                    case 3:frequency=350+240*Math.sin(Math.PI*span);break;
                    default:frequency=390-280*span;
                }
                double osc=Math.sin(2*Math.PI*frequency*t);
                if(kind==1)osc=osc*.52+(rand.nextDouble()*2-1)*.48;
                if(kind==2)osc=osc*.65+Math.sin(2*Math.PI*(frequency*1.25)*t)*.35;
                short val=(short)(Math.max(-1,Math.min(1,osc*decay*.30))*32767);
                le(out,val&65535,2);
            }
        }
    }
    private static void le(FileOutputStream out,int n,int bytes)throws Exception{
        for(int i=0;i<bytes;i++)out.write((n>>>(8*i))&255);
    }
}
