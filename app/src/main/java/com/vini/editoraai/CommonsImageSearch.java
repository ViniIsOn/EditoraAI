package com.vini.editoraai;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Busca por fotos com metadados de licença; não usa scraping de sites comerciais. */
public final class CommonsImageSearch {
    public static final class Result {
        public final String title,url,license,credit,pageUrl;
        public Result(String title,String url,String license,String credit,String pageUrl) {
            this.title=title;this.url=url;this.license=license;this.credit=credit;this.pageUrl=pageUrl;
        }
    }
    private static final String API="https://commons.wikimedia.org/w/api.php";
    private CommonsImageSearch(){}
    public static List<Result> search(String query) throws Exception {
        String request=API+"?action=query&format=json&generator=search&gsrnamespace=6"
                +"&gsrlimit=12&gsrsearch="+URLEncoder.encode(query,"UTF-8")
                +"&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=1000";
        HttpURLConnection c=open(request);
        try {
            if(c.getResponseCode()!=200)throw new Exception("Servidor respondeu HTTP "+c.getResponseCode());
            byte[] data=readLimited(c.getInputStream(),1200000);
            JSONObject pages=new JSONObject(new String(data,"UTF-8"))
                    .optJSONObject("query");
            ArrayList<Result> result=new ArrayList<>();
            if(pages==null||pages.optJSONObject("pages")==null)return result;
            JSONObject entries=pages.optJSONObject("pages");
            Iterator<String> keys=entries.keys();
            while(keys.hasNext()&&result.size()<9) {
                JSONObject page=entries.optJSONObject(keys.next());
                if(page==null||page.optJSONArray("imageinfo")==null)continue;
                JSONObject image=page.optJSONArray("imageinfo").optJSONObject(0);
                if(image==null)continue;
                JSONObject metadata=image.optJSONObject("extmetadata");
                String license=metadata==null?"":metadata.optJSONObject("LicenseShortName")==null?""
                        :metadata.optJSONObject("LicenseShortName").optString("value","");
                String norm=license.toLowerCase(Locale.ROOT);
                // Licenças com atribuição exigem crédito; filtrar conteúdo sem licença explícita.
                if(!(norm.contains("cc")||norm.contains("public domain")||norm.contains("público")))continue;
                String url=image.optString("thumburl",image.optString("url",""));
                if(url.isEmpty()||!url.startsWith("https://"))continue;
                String artist=metadata==null?"":metadata.optJSONObject("Artist")==null?""
                        :metadata.optJSONObject("Artist").optString("value","").replaceAll("<[^>]*>","").trim();
                String original=image.optString("descriptionurl","");
                result.add(new Result(page.optString("title","Imagem"),
                        url,license,artist,original));
            }
            return result;
        } finally{ c.disconnect(); }
    }
    public static Uri download(Context ctx,String url) throws Exception{
        if(!url.startsWith("https://upload.wikimedia.org/"))
            throw new IllegalArgumentException("Somente mídia hospedada pelo Wikimedia Commons.");
        HttpURLConnection c=open(url);
        try{
            if(c.getResponseCode()!=200)throw new Exception("Imagem indisponível ("+c.getResponseCode()+")");
            String type=c.getContentType();
            if(type!=null&&!type.startsWith("image/"))
                throw new Exception("O servidor não retornou uma imagem.");
            File dir=new File(ctx.getFilesDir(),"library");
            if(!dir.exists()&&!dir.mkdirs())throw new Exception("Sem acesso à biblioteca.");
            File file=File.createTempFile("commons_",".img",dir);
            try(FileOutputStream stream=new FileOutputStream(file)){
                byte[] bytes=readLimited(c.getInputStream(),8*1024*1024);
                stream.write(bytes);
            }catch(Exception error){file.delete();throw error;}
            try(java.io.FileInputStream stream=new java.io.FileInputStream(file)){
                if(android.graphics.BitmapFactory.decodeStream(stream)==null){
                    file.delete();throw new Exception("Imagem não compatível.");
                }
            }
            return Uri.fromFile(file);
        }finally{c.disconnect();}
    }
    private static HttpURLConnection open(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent","EditoraAI/0.5 (Android; ViniIsOn EditoraAI)");
        c.setRequestProperty("Accept-Encoding","identity");
        return c;
    }
    private static byte[] readLimited(InputStream src,int limit)throws Exception{
        try(BufferedInputStream in=new BufferedInputStream(src);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){
                if(out.size()+n>limit)throw new Exception("Arquivo excedeu o limite de segurança.");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
}
