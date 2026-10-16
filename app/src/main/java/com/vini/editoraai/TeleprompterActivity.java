package com.vini.editoraai;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.core.MirrorMode;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Gravação local com câmera frontal + teleprompter que NÃO fica gravado no MP4. */
public final class TeleprompterActivity extends ComponentActivity {
    private static final int REQ_PERMISSIONS=180;
    private static final int BG=Color.rgb(9,15,29), PANEL=Color.rgb(21,35,55);
    private static final int WHITE=Color.rgb(237,245,252), CYAN=Color.rgb(66,228,232);
    private static final String DEFAULT_SCRIPT =
            "Vocês já conhecem essa arara azul aqui, né?\n\n"
            +"Mas ninguém sabe quem está por trás dela...\n\n"
            +"Depois de 300 seguidores, finalmente chegou a hora!\n\n"
            +"Preparados? Três... dois... um...\n\n"
            +"SURPRESAAAA! KKKKK!\n\n"
            +"Pois é, pessoal! Eu sou a pessoa por trás do Poder Azul! "
            +"E não, eu não sou uma arara na vida real!\n\n"
            +"Muito obrigado pelos 300 seguidores!\n\n"
            +"Agora vou aparecer nos vídeos falando de jogos, curiosidades, natureza, "
            +"psicologia e mensagens de apoio!\n\n"
            +"Mas o Poder Azul não foi demitido, não! "
            +"Ele vai continuar como meu avatar VTuber nas lives!\n\n"
            +"Valeu, pessoal, e até o próximo vídeo!";

    private static final String SONIC_RUMBLE_SCRIPT =
            "Você conhece Sonic Rumble? Então olha três curiosidades que talvez você não saiba!\n\n"
            +"Primeira: o jogo acontece num mundo de brinquedos criado pelo doutor Eggman. Por isso os personagens parecem bonequinhos!\n\n"
            +"Segunda: as partidas podem reunir até trinta e dois jogadores, com desafios de obstáculos, sobrevivência e coleta de Rings!\n\n"
            +"Terceira: alguns mapas são inspirados em lugares clássicos do Sonic, como Green Hill e Sky Sanctuary!\n\n"
            +"Agora deixa eu mostrar minha conta! Já cheguei ao nível vinte e um e tenho um Sonic dourado de raridade épica.\n\n"
            +"Meu personagem favorito é o Vector, que eu já desbloqueei e é raro. Mas costumo jogar com o Sonic dourado porque ele é épico!\n\n"
            +"E o seu favorito? Comenta aí e segue o Poder Azul para mais curiosidades!";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PreviewView preview;
    private ScrollView scriptScroll;
    private TextView scriptView, status, speedLabel, sizeLabel, timeLabel;
    private Button recordButton, pauseButton, editButton;
    private SeekBar speedBar, sizeBar;
    private ProcessCameraProvider provider;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private boolean cameraReady=false, isSaving=false, paused=false, scrolling=false;
    private int countdown=0;
    private long lastTick=0;
    private double scrollFraction=0;
    private int speedDpPerSecond=46, textSizeSp=29;
    private String script="";
    private File oldAndroidFile;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SharedPreferences prefs=getSharedPreferences("teleprompter",MODE_PRIVATE);
        script=prefs.getString("script","");
        speedDpPerSecond=prefs.getInt("speed",46);
        textSizeSp=prefs.getInt("size",29);
        buildScreen();
        checkPermissions();
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){
        TextView tv=new TextView(this);
        tv.setText(text);tv.setTextSize(size);tv.setTextColor(color);
        return tv;
    }
    private Button button(String text,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(WHITE);
        b.setTextSize(13);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void gap(LinearLayout root,int h){
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));
    }
    private void buildScreen(){
        ScrollView whole=new ScrollView(this);
        whole.setFillViewport(true);whole.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15),dp(15),dp(15),dp(28));
        whole.addView(root);setContentView(whole);
        root.addView(label("🎥 GRAVAR COM ROTEIRO",22,WHITE));
        gap(root,4);
        root.addView(label("Selecione Sonic Rumble, Poder Azul ou comece em branco. Use Próxima fala para não esperar a rolagem. O texto não aparece no MP4.",12,CYAN));
        gap(root,10);
        FrameLayout stage=new FrameLayout(this);
        LinearLayout.LayoutParams stageParams=new LinearLayout.LayoutParams(-1,dp(445));
        root.addView(stage,stageParams);
        preview=new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);
        shade.setBackgroundColor(Color.argb(75,0,0,0));
        stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        scriptScroll=new ScrollView(this);
        scriptScroll.setVerticalScrollBarEnabled(false);
        scriptScroll.setFillViewport(false);
        GradientDrawable box=new GradientDrawable();
        box.setColor(Color.argb(198,4,13,30));
        box.setCornerRadius(dp(15));
        box.setStroke(dp(1),Color.argb(160,66,228,232));
        scriptScroll.setBackground(box);
        scriptView=label(script,textSizeSp,WHITE);
        scriptView.setGravity(Gravity.CENTER_HORIZONTAL);
        scriptView.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        scriptView.setPadding(dp(14),dp(35),dp(14),dp(135));
        scriptView.setLineSpacing(dp(7),1.08f);
        scriptScroll.addView(scriptView,new ScrollView.LayoutParams(-1,-2));
        FrameLayout.LayoutParams lines=new FrameLayout.LayoutParams(-1,dp(223),Gravity.TOP);
        lines.leftMargin=dp(12);lines.rightMargin=dp(12);lines.topMargin=dp(20);
        stage.addView(scriptScroll,lines);
        timeLabel=label("● PRONTO • 00:00",14,WHITE);
        timeLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        timeLabel.setPadding(dp(10),dp(7),dp(10),dp(7));
        timeLabel.setBackgroundColor(Color.argb(190,0,0,0));
        FrameLayout.LayoutParams timer=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        timer.bottomMargin=dp(11);stage.addView(timeLabel,timer);
        gap(root,10);
        status=label("Preparando a câmera frontal...",13,CYAN);root.addView(status);
        gap(root,8);
        recordButton=button("● GRAVAR VÍDEO",this::recordOrStop);
        recordButton.setEnabled(false);
        root.addView(recordButton,new LinearLayout.LayoutParams(-1,dp(52)));
        pauseButton=button("Ⅱ PAUSAR",this::togglePause);
        pauseButton.setVisibility(View.GONE);
        root.addView(pauseButton,new LinearLayout.LayoutParams(-1,dp(48)));
        gap(root,7);
        root.addView(button("📄 Escolher roteiro pronto: Sonic Rumble / Poder Azul / Em branco",
                this::choosePreset));
        editButton=button("✎ Editar / colar roteiro",this::editScript);
        root.addView(editButton);
        root.addView(button("⏩ Ir para a PRÓXIMA fala",this::jumpNextSpeech));
        root.addView(button("↺ Voltar ao início do roteiro",()->{
            if(recording==null){scriptScroll.scrollTo(0,0);scrollFraction=0;}
            else status.setText("Pause a gravação antes de reiniciar o roteiro.");
        }));
        gap(root,6);
        speedLabel=label("",13,WHITE);root.addView(speedLabel);
        speedBar=new SeekBar(this);speedBar.setMax(120);
        speedBar.setProgress(Math.max(0,Math.min(120,speedDpPerSecond-12)));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int value,boolean fromUser){
                speedDpPerSecond=value+12;updateLabels();
                if(fromUser)saveSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });root.addView(speedBar);
        sizeLabel=label("",13,WHITE);root.addView(sizeLabel);
        sizeBar=new SeekBar(this);sizeBar.setMax(26);
        sizeBar.setProgress(Math.max(0,Math.min(26,textSizeSp-18)));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int value,boolean fromUser){
                textSizeSp=value+18;
                scriptView.setTextSize(textSizeSp);
                updateLabels();
                if(fromUser)saveSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });root.addView(sizeBar);
        root.addView(label("Dica: olhe próximo da lente. Faça um teste de 10 segundos e confira se o áudio ficou claro antes de gravar o vídeo inteiro.",12,CYAN));
        gap(root,10);
        root.addView(button("← Voltar ao EditoraAI",this::leaveRecorder));
        updateLabels();
    }

    private void updateLabels(){
        if(speedLabel!=null)speedLabel.setText("Velocidade do roteiro: "+speedDpPerSecond+" px/s");
        if(sizeLabel!=null)sizeLabel.setText("Tamanho da letra: "+textSizeSp);
    }
    private void saveSettings(){
        getSharedPreferences("teleprompter",MODE_PRIVATE).edit()
                .putString("script",script)
                .putInt("speed",speedDpPerSecond)
                .putInt("size",textSizeSp).apply();
    }
    private void choosePreset(){
        if(recording!=null||isSaving||countdown>0){
            status.setText("Termine a gravação para trocar de roteiro.");
            return;
        }
        final String[] presets={"🎮 Curiosidades Sonic Rumble","🦜 Revelação Poder Azul • 300 seguidores",
                "✎ Começar em branco","Cancelar"};
        new AlertDialog.Builder(this).setTitle("Qual vídeo vai gravar?")
                .setItems(presets,(dialog,which)->{
                    if(which==3)return;
                    script=which==0?SONIC_RUMBLE_SCRIPT:which==1?DEFAULT_SCRIPT:"";
                    scriptView.setText(script);
                    scriptScroll.scrollTo(0,0);
                    scrollFraction=0;
                    saveSettings();
                    status.setText(which==2?"Roteiro vazio. Cole ou escreva suas falas.":
                            "Roteiro pronto! Pode ajustar, gravar ou pular para a próxima fala.");
                }).show();
    }

    /** Salta parágrafos na tela sem aguardar a rolagem; não muda a gravação. */
    private void jumpNextSpeech(){
        if(script.trim().isEmpty()){status.setText("Escolha ou cole um roteiro primeiro.");return;}
        scriptScroll.post(()->{
            android.text.Layout layout=scriptView.getLayout();
            if(layout==null)return;
            int first=layout.getOffsetForVertical(Math.max(0,
                    scriptScroll.getScrollY()+dp(23)));
            int next=script.indexOf("\n\n",Math.min(script.length(),first+1));
            if(next<0){scriptScroll.fullScroll(View.FOCUS_DOWN);
                status.setText("Você chegou ao final do roteiro.");return;}
            int after=next+2;
            while(after<script.length() && script.charAt(after)=='\n')after++;
            int line=layout.getLineForOffset(Math.min(script.length(),after));
            scriptScroll.smoothScrollTo(0,layout.getLineTop(line));
            status.setText("Próxima fala! Pode continuar sem esperar.");
        });
    }
    private void editScript(){
        if(recording!=null||isSaving||countdown>0){status.setText("Termine a gravação antes de editar o roteiro.");return;}
        EditText input=new EditText(this);
        input.setText(script);
        input.setTextSize(16);
        input.setMinLines(9);input.setMaxLines(18);
        input.setGravity(Gravity.TOP);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                |android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                |android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad=dp(13);
        FrameLayout holder=new FrameLayout(this);
        holder.setPadding(pad,0,pad,0);
        holder.addView(input);
        new AlertDialog.Builder(this).setTitle("Roteiro para ler durante a gravação")
                .setView(holder).setPositiveButton("Salvar texto",(d,w)->{
                    String edited=input.getText().toString().trim();
                    if(edited.isEmpty()){
                        status.setText("O roteiro não pode ficar vazio.");
                        return;
                    }
                    script=edited;scriptView.setText(script);scriptScroll.scrollTo(0,0);
                    scrollFraction=0;
                    saveSettings();
                    status.setText("Roteiro salvo. Pronto para gravar!");
                }).setNegativeButton("Cancelar",null).show();
    }
    private boolean permissionsOk(){
        return ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
            &&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
    }
    private void checkPermissions(){
        if(permissionsOk()){openCamera();return;}
        if(Build.VERSION.SDK_INT>=23)
            requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},REQ_PERMISSIONS);
    }
    @Override public void onRequestPermissionsResult(int req,String[] names,int[] grants){
        super.onRequestPermissionsResult(req,names,grants);
        if(req!=REQ_PERMISSIONS)return;
        if(permissionsOk())openCamera();
        else {
            status.setText("Para gravar seu rosto e voz, autorize Câmera e Microfone nas permissões do Android.");
            recordButton.setEnabled(false);
        }
    }

    private void openCamera(){
        if(cameraReady)return;
        status.setText("Abrindo a câmera e o microfone...");
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try{
                provider=future.get();
                Preview camPreview=new Preview.Builder().build();
                camPreview.setSurfaceProvider(preview.getSurfaceProvider());
                QualitySelector selection=QualitySelector.from(
                        Quality.HD,FallbackStrategy.higherQualityOrLowerThan(Quality.HD));
                Recorder recorder=new Recorder.Builder().setQualitySelector(selection).build();
                videoCapture=new VideoCapture.Builder<>(recorder)
                        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build();
                provider.unbindAll();
                CameraSelector selector=provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        ?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;
                provider.bindToLifecycle(this,selector,camPreview,videoCapture);
                cameraReady=true;
                recordButton.setEnabled(true);
                status.setText("Câmera pronta. Cole seu roteiro e toque em Gravar.");
            }catch(Exception ex){
                cameraReady=false;recordButton.setEnabled(false);
                status.setText("Não consegui abrir a câmera: "+ex.getMessage());
            }
        },ContextCompat.getMainExecutor(this));
    }

    private void recordOrStop(){
        if(recording!=null){stopRecording();return;}
        if(isSaving||countdown>0)return;
        if(!cameraReady||!permissionsOk()){checkPermissions();return;}
        if(script.trim().isEmpty()){status.setText("Primeiro escreva o roteiro.");return;}
        countdown=3;
        recordButton.setEnabled(false);
        status.setText("Gravação começará em 3...");
        beginCountdown();
    }
    private void beginCountdown(){
        if(countdown<=0){startRecording();return;}
        int now=countdown;
        timeLabel.setText("● PREPARE-SE • "+now);
        status.setText("Olhe para a câmera! Começando em "+now+"...");
        countdown--;
        handler.postDelayed(this::beginCountdown,1000);
    }

    private void startRecording(){
        if(!permissionsOk()||videoCapture==null){countdown=0;recordButton.setEnabled(true);return;}
        try{
            String name="EditoraAI_Rosto_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US)
                    .format(new Date())+".mp4";
            PendingRecording pending;
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME,name);
                values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES+"/EditoraAI");
                MediaStoreOutputOptions options=new MediaStoreOutputOptions.Builder(
                        getContentResolver(),MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                        .setContentValues(values).build();
                pending=videoCapture.getOutput().prepareRecording(this,options);
                oldAndroidFile=null;
            }else{
                File dir=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                if(dir==null)throw new Exception("Pasta de vídeos indisponível.");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("Não consegui criar a pasta de gravações.");
                oldAndroidFile=new File(dir,name);
                pending=videoCapture.getOutput().prepareRecording(this,
                        new FileOutputOptions.Builder(oldAndroidFile).build());
            }
            scrollFraction=0;
            recording=pending.withAudioEnabled().start(
                    ContextCompat.getMainExecutor(this),this::onRecordingEvent);
            recordButton.setText("■ PARAR E USAR VÍDEO");
            recordButton.setEnabled(true);
            pauseButton.setVisibility(View.VISIBLE);
            editButton.setEnabled(false);
            status.setText("Iniciando gravação...");
        }catch(Exception ex){
            recordButton.setEnabled(true);pauseButton.setVisibility(View.GONE);
            editButton.setEnabled(true);status.setText("Não foi possível começar: "+ex.getMessage());
        }
    }
    private void onRecordingEvent(VideoRecordEvent event){
        if(event instanceof VideoRecordEvent.Start){
            paused=false;scrolling=true;
            lastTick=SystemClock.uptimeMillis();
            handler.removeCallbacks(scrollTick);
            handler.post(scrollTick);
            timeLabel.setText("● GRAVANDO • 00:00");
            status.setText("Gravando sua voz e câmera. O roteiro é invisível no MP4.");
        }else if(event instanceof VideoRecordEvent.Status){
            VideoRecordEvent.Status stat=(VideoRecordEvent.Status)event;
            long seconds=stat.getRecordingStats().getRecordedDurationNanos()/1000000000L;
            timeLabel.setText(String.format(Locale.US,"● %s • %02d:%02d",
                    paused?"PAUSADO":"GRAVANDO",seconds/60,seconds%60));
        }else if(event instanceof VideoRecordEvent.Finalize){
            scrolling=false;handler.removeCallbacks(scrollTick);
            isSaving=false;recording=null;
            VideoRecordEvent.Finalize finalized=(VideoRecordEvent.Finalize)event;
            if(finalized.hasError()){
                status.setText("Erro ao finalizar a gravação: "+finalized.getError());
                recordButton.setText("● TENTAR NOVAMENTE");
                recordButton.setEnabled(true);pauseButton.setVisibility(View.GONE);
                editButton.setEnabled(true);return;
            }
            Uri file=finalized.getOutputResults().getOutputUri();
            if((file==null||Uri.EMPTY.equals(file))&&oldAndroidFile!=null)file=Uri.fromFile(oldAndroidFile);
            if(file==null||Uri.EMPTY.equals(file)){
                status.setText("Gravação finalizada, mas não consegui obter o arquivo.");
                recordButton.setEnabled(true);editButton.setEnabled(true);return;
            }
            final Uri completed=file;
            if(oldAndroidFile!=null) {
                MediaScannerConnection.scanFile(this,
                        new String[]{oldAndroidFile.getAbsolutePath()},new String[]{"video/mp4"},null);
            }
            status.setText("Vídeo salvo! Abrindo no EditoraAI...");
            Intent result=new Intent();result.setData(completed);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK,result);
            finish();
        }
    }
    private final Runnable scrollTick=new Runnable(){
        @Override public void run(){
            if(!scrolling||isFinishing())return;
            long now=SystemClock.uptimeMillis();
            long dt=Math.min(100,Math.max(0,now-lastTick));
            lastTick=now;
            scrollFraction+=dt*speedDpPerSecond*getResources().getDisplayMetrics().density/1000.0;
            int move=(int)scrollFraction;
            if(move>0){
                scrollFraction-=move;
                int max=Math.max(0,scriptView.getHeight()-scriptScroll.getHeight());
                scriptScroll.scrollTo(0,Math.min(max,scriptScroll.getScrollY()+move));
            }
            handler.postDelayed(this,32);
        }
    };
    private void togglePause(){
        if(recording==null||isSaving)return;
        try{
            if(paused){
                recording.resume();
                paused=false;scrolling=true;
                lastTick=SystemClock.uptimeMillis();
                handler.post(scrollTick);
                pauseButton.setText("Ⅱ PAUSAR");
                status.setText("Gravação e teleprompter retomados.");
            }else{
                recording.pause();
                paused=true;scrolling=false;handler.removeCallbacks(scrollTick);
                pauseButton.setText("▶ RETOMAR");
                status.setText("Gravação pausada; o roteiro também parou.");
            }
        }catch(Exception ex){status.setText("Não foi possível pausar/retomar: "+ex.getMessage());}
    }
    private void stopRecording(){
        if(recording==null||isSaving)return;
        scrolling=false;handler.removeCallbacks(scrollTick);
        isSaving=true;
        recordButton.setEnabled(false);
        pauseButton.setVisibility(View.GONE);
        status.setText("Salvando seu vídeo na galeria, aguarde...");
        recording.stop();
    }
    private void leaveRecorder(){
        if(countdown>0){
            new AlertDialog.Builder(this).setTitle("Preparando gravação")
                    .setMessage("A contagem está em andamento. Aguarde começar e depois pare a gravação para salvar.")
                    .setPositiveButton("Entendi",null).show();
            return;
        }
        if(recording!=null){
            new AlertDialog.Builder(this).setTitle("Gravação em andamento")
                    .setMessage("Quer parar e salvar o vídeo antes de voltar ao editor?")
                    .setPositiveButton("Parar e salvar",(d,w)->stopRecording())
                    .setNegativeButton("Continuar gravando",null).show();
        }else if(isSaving){
            status.setText("Aguarde o fim do salvamento.");
        }else finish();
    }
    @Override public void onBackPressed(){leaveRecorder();}
    @Override protected void onDestroy(){
        scrolling=false;handler.removeCallbacksAndMessages(null);
        if(recording!=null){
            try{recording.stop();}catch(Exception ignored){}
            recording=null;
        }
        super.onDestroy();
    }
}
