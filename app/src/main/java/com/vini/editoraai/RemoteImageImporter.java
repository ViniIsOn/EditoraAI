package com.vini.editoraai;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Movie;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

/** Um único arquivo HTTPS, limitado a 8 MB, escolhido explicitamente pelo usuário. */
public final class RemoteImageImporter {
    private RemoteImageImporter(){}
    private static void validate(URL url)throws Exception {
        if(!"https".equalsIgnoreCase(url.getProtocol()))throw new Exception("A URL deve começar com HTTPS.");
        if(url.getUserInfo()!=null || url.getPort()!=-1)throw new Exception("Endereço não aceito.");
        String host=url.getHost().toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".local")||
                host.endsWith(".internal")||host.isEmpty())
            throw new Exception("Endereço privado não permitido.");
        // Bloqueia endereços IP explícitos e domínios disfarçados de IP.
        if(host.matches("[0-9.]+") || host.contains(":"))
            throw new Exception("Use o endereço público de um site HTTPS, não um IP.");
    }
    public static Uri importSelected(Context context,String link)throws Exception {
        URL url=new URL(link.trim());
        validate(url);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(12000);conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent","EditoraAI/0.6 Android");
        File target=null;
        try {
            int response=conn.getResponseCode();
            if(response>=300&&response<400)throw new Exception("A imagem redireciona para outro site. Use o endereço direto do arquivo.");
            if(response!=200)throw new Exception("Servidor respondeu HTTP "+response);
            String type=conn.getContentType();
            if(type==null||!(type.toLowerCase(Locale.ROOT).startsWith("image/jpeg")||
                    type.toLowerCase(Locale.ROOT).startsWith("image/png")||
                    type.toLowerCase(Locale.ROOT).startsWith("image/webp")||
                    type.toLowerCase(Locale.ROOT).startsWith("image/gif")))
                throw new Exception("A URL não retornou JPEG, PNG, WebP ou GIF.");
            String suffix=type.toLowerCase(Locale.ROOT).startsWith("image/gif")?".gif":".img";
            File dir=new File(context.getFilesDir(),"library");
            if(!dir.isDirectory()&&!dir.mkdirs())throw new Exception("Falha no armazenamento interno.");
            target=File.createTempFile("online_",suffix,dir);
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(target)){
                byte[] data=new byte[16384];long count=0;int n;
                while((n=in.read(data))!=-1){
                    count+=n;
                    if(count>8L*1024*1024)throw new Exception("Imagem acima de 8 MB; escolha uma menor.");
                    out.write(data,0,n);
                }
            }
            boolean ok;
            try(java.io.FileInputStream in=new java.io.FileInputStream(target)){
                ok=type.toLowerCase(Locale.ROOT).startsWith("image/gif")
                    ?Movie.decodeStream(in)!=null:BitmapFactory.decodeStream(in)!=null;
            }
            if(!ok)throw new Exception("Arquivo não é uma imagem válida.");
            return Uri.fromFile(target);
        }catch(Exception error){
            if(target!=null&&target.exists())target.delete();
            throw error;
        }finally{conn.disconnect();}
    }
}
