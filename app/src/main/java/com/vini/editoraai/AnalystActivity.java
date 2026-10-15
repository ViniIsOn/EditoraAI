package com.vini.editoraai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Área do analista: sugere cenas a partir de legendas e insere mídias aprovadas. */
public final class AnalystActivity extends Activity {
    private static final int REQ_IMAGE=300,REQ_SOUND=301;
    private static final int BG=Color.rgb(9,15,29),PANEL=Color.rgb(22,34,53);
    private static final int CYAN=Color.rgb(51,219,223),WHITE=Color.rgb(232,241,250);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<MediaCue> media=new ArrayList<>();
    private final List<Caption> captions=new ArrayList<>();
    private LinearLayout ideas,list;
    private TextView status;
    private Uri video;
    private long durationMs,requestedStartMs,requestedEndMs;
    private String requestedKind="image";
    private int busyCount=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        String path=getIntent().getStringExtra("video");
        if(path==null||path.isEmpty()){finish();return;}
        video=Uri.parse(path);
        durationMs=getIntent().getLongExtra("duration",0);
        requestedStartMs=getIntent().getLongExtra("position",0);
        media.addAll(MediaCueStore.load(this,video));
        try{
            String raw=getSharedPreferences("editor",MODE_PRIVATE).getString("captions","[]");
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++)captions.add(Caption.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        createScreen();
        showMedia();
    }
    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}
    private TextView text(String t,int sp,int color){
        TextView view=new TextView(this);view.setText(t);view.setTextColor(color);
        view.setTextSize(sp);view.setPadding(dp(2),dp(4),dp(2),dp(4));return view;
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private Button button(String title,Runnable run) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(WHITE);
        b.setTextSize(13);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        b.setOnClickListener(v->run.run());return b;
    }
    private void space(LinearLayout l){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(11)));}
    private String clock(long ms){
        return String.format(Locale.ROOT,"%02d:%02d",ms/60000,(ms/1000)%60);
    }
    private long end(long start,long amount){
        long finish=start+amount;
        return durationMs>start?Math.min(durationMs,finish):finish;
    }
    private void message(String v){if(status!=null)status.setText(v);}
    private void createScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=vertical();root.setPadding(dp(16),dp(21),dp(16),dp(34));scroll.addView(root);
        setContentView(scroll);
        root.addView(text("✦ EDITORAAI • ANALISTA",24,WHITE));
        root.addView(text("A análise de palavras-chave funciona no celular. Você escolhe o que inserir e em qual trecho.",13,CYAN));
        space(root);
        root.addView(button("← Voltar ao editor",this::finish));
        root.addView(button("✦ Analisar transcrição e sugerir cenas",this::analyze));
        root.addView(button("🌐 Buscar imagens com licença no Wikimedia Commons",()->
                askSearch("",requestedStartMs,"image")));
        root.addView(button("🖼 Importar minha imagem",()->pickImage(requestedStartMs,"image")));
        root.addView(button("😆 Importar meu meme PNG / JPG / GIF",()->pickImage(requestedStartMs,"meme")));
        root.addView(button("🔊 Inserir efeito sonoro original",this::chooseSound));
        root.addView(button("♫ Importar efeito sonoro do celular",this::pickAudio));
        status=text("Pronto. As mídias que você aprovar serão salvas no projeto.",13,CYAN);
        root.addView(status);
        space(root);
        root.addView(text("IDEIAS A PARTIR DA SUA FALA",16,WHITE));
        ideas=vertical();root.addView(ideas);
        ideas.addView(text("Use 'Analisar transcrição' após criar as legendas no editor.",12,WHITE));
        space(root);
        root.addView(text("MÍDIAS NA LINHA DO TEMPO",16,WHITE));
        list=vertical();root.addView(list);
        root.addView(text("A busca usa Wikimedia Commons e exibe a licença. Para prints de jogos, use imagens próprias ou que você tenha autorização para reutilizar. A inclusão nunca é automática sem aprovação.",12,CYAN));
    }
    private void analyze(){
        if(captions.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Nenhuma fala transcrita")
                .setMessage("Volte ao editor, gere legendas offline e depois entre novamente no Analista.")
                .setPositiveButton("Entendi",null).show();return;
        }
        List<ScriptAnalyst.Idea> suggestions=ScriptAnalyst.analyze(captions,durationMs);
        ideas.removeAllViews();
        for(ScriptAnalyst.Idea idea:suggestions){
            ideas.addView(text(clock(idea.startMs)+" • "+idea.query,14,WHITE));
            ideas.addView(text(idea.why,12,CYAN));
            ideas.addView(button("🌐 Buscar imagem deste tema",()->askSearch(idea.query,idea.startMs,"image")));
            ideas.addView(button("🖼 Usar imagem minha neste ponto",()->pickImage(idea.startMs,"image")));
            space(ideas);
        }
        if(suggestions.isEmpty())ideas.addView(text("Não encontrei temas claros. Use a importação manual.",13,WHITE));
        message("Analisei as legendas e encontrei "+suggestions.size()+" sugestões. Escolha as que combinam com o vídeo.");
    }
    private void pickImage(long start,String kind){
        requestedStartMs=Math.max(0,start);
        requestedEndMs=end(requestedStartMs,3600);
        requestedKind=kind;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,REQ_IMAGE);
    }
    private void pickAudio(){
        requestedStartMs=Math.max(0,requestedStartMs);
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,REQ_SOUND);
    }
    private String name(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "Minha mídia";
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(SecurityException ignored){}
        if(request==REQ_IMAGE){
            saveCue(new MediaCue(requestedKind,uri.toString(),name(uri),"Minha mídia","",
                    requestedStartMs,requestedEndMs,1));
        }else if(request==REQ_SOUND) {
            saveCue(new MediaCue("sfx",uri.toString(),name(uri),"Meu áudio","custom",
                    requestedStartMs,end(requestedStartMs,1600),0));
        }
    }
    private void chooseSound(){
        new AlertDialog.Builder(this).setTitle("Efeitos originais, livres de áudios de memes")
            .setItems(SfxLibrary.NAMES,(dialog,index)->{
                long start=requestedStartMs;
                saveCue(new MediaCue("sfx","",SfxLibrary.NAMES[index],"Criado pelo EditoraAI",
                    String.valueOf(index),start,end(start,SfxLibrary.durationMs(index)),0));
            }).setNegativeButton("Cancelar",null).show();
    }
    private void askSearch(String initial,long start,String kind){
        EditText query=new EditText(this);query.setSingleLine(true);query.setText(initial);
        query.setHint("Ex.: Sonic, floresta, arara azul");
        new AlertDialog.Builder(this).setTitle("Pesquisar Wikimedia Commons")
            .setMessage("Buscaremos imagens com indicação de licença aberta. Pode não haver arquivos de jogos comerciais.")
            .setView(query).setNegativeButton("Cancelar",null)
            .setPositiveButton("Pesquisar",(d,w)->{
                String q=query.getText().toString().trim();
                if(!q.isEmpty())search(q,start,kind);
            }).show();
    }
    private void search(String query,long start,String kind){
        message("Pesquisando “"+query+"”...");
        io.execute(()->{
            try{
                List<CommonsImageSearch.Result> results=CommonsImageSearch.search(query);
                main.post(()->{
                    if(isFinishing())return;
                    if(results.isEmpty()){
                        new AlertDialog.Builder(this).setTitle("Sem imagens abertas encontradas")
                            .setMessage("Não encontrei imagens com licenças identificadas para esse tema. Você pode importar suas próprias imagens.")
                            .setPositiveButton("Importar minha imagem",(d,w)->pickImage(start,kind))
                            .setNegativeButton("Fechar",null).show();return;
                    }
                    String[] names=new String[results.size()];
                    for(int i=0;i<names.length;i++)names[i]=results.get(i).title+
                            "\n"+results.get(i).license;
                    new AlertDialog.Builder(this).setTitle("Escolha um arquivo com licença aberta")
                        .setItems(names,(d,index)->approve(results.get(index),start,kind))
                        .setNegativeButton("Cancelar",null).show();
                    message(results.size()+" resultados encontrados. Toque para conferir o crédito e aprovar.");
                });
            }catch(Exception e){main.post(()->message("Busca indisponível: "+e.getMessage()));}
        });
    }
    private void approve(CommonsImageSearch.Result result,long start,String kind){
        String credits=result.license+" • "+result.credit+"\n"+result.pageUrl;
        new AlertDialog.Builder(this).setTitle("Autorizar download e inserção?")
            .setMessage(result.title+"\n\nCrédito/licença: "+credits+
                    "\n\nRespeite as condições de atribuição ao publicar. Esta mídia será baixada somente com sua confirmação.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Baixar e inserir",(d,w)->{
                message("Baixando imagem aprovada...");
                io.execute(()->{
                    try{
                        Uri local=CommonsImageSearch.download(getApplicationContext(),result.url);
                        main.post(()->saveCue(new MediaCue(kind,local.toString(),
                                result.title,credits,"",start,end(start,3600),1)));
                    }catch(Exception e){main.post(()->message("Download falhou: "+e.getMessage()));}
                });
            }).show();
    }
    private void saveCue(MediaCue cue){
        if(media.size()>=30){message("O projeto atingiu o limite de 30 mídias.");return;}
        if(cue.endMs<=cue.startMs){message("Não há duração suficiente nesse trecho.");return;}
        media.add(cue);MediaCueStore.save(this,video,media);
        showMedia();
        message("Inseri “"+cue.label+"” em "+clock(cue.startMs)+". Volte ao editor para exportar.");
    }
    private void showMedia(){
        if(list==null)return;
        list.removeAllViews();
        if(media.isEmpty()){
            list.addView(text("Ainda sem imagens e sons inseridos.",12,WHITE));return;
        }
        for(MediaCue cue:new ArrayList<>(media)){
            list.addView(text(clock(cue.startMs)+" – "+clock(cue.endMs)+"  • "+cue.kind+
                    "\n"+cue.label,13,WHITE));
            if(cue.isImage()){
                ImageView image=new ImageView(this);
                image.setImageURI(Uri.parse(cue.uri));
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                list.addView(image,new LinearLayout.LayoutParams(-1,dp(115)));
            }
            list.addView(button("Remover este item",()->{
                media.remove(cue);MediaCueStore.save(this,video,media);showMedia();
            }));
            space(list);
        }
    }
    @Override protected void onDestroy(){
        io.shutdownNow();
        super.onDestroy();
    }
}
