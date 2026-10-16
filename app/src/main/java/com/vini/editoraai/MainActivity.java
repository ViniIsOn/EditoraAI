package com.vini.editoraai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int REQ_VIDEO = 71;
    private static final int REQ_MUSIC = 72;
    private static final int REQ_FONT = 73;
    private static final int REQ_ANALYST = 74;
    private static final int BG = Color.rgb(9, 15, 29);
    private static final int PANEL = Color.rgb(20, 30, 49);
    private static final int CYAN = Color.rgb(44, 216, 221);
    private static final int LIGHT = Color.rgb(232, 239, 250);
    private static final int MUTED = Color.rgb(151, 166, 189);
    private static final int STROKE = Color.rgb(43, 62, 88);

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Caption> captions = new ArrayList<>();
    private List<MediaCue> activeMedia=new ArrayList<>();
    private MediaCue shownMedia;
    private ImageView mediaImage;
    private final VideoExporter exporter = new VideoExporter();
    private ExoPlayer player;
    private Uri videoUri, musicUri, completedUri;
    private String groqKey = ""; // memory-only, never saved to disk
    private long durationMs = 0, trimStartMs = 0, trimEndMs = 0;
    private boolean busy = false;
    private SeekBar startSeek, endSeek, timelineSeek, voiceVolumeSeek, musicVolumeSeek, musicStartSeek, duckVolumeSeek;
    private TextView voiceVolumeLabel, musicVolumeLabel, musicStartLabel, duckVolumeLabel;
    private int voiceVolumePercent=100, musicVolumePercent=26, duckVolumePercent=28;
    private long musicStartMs=0;
    private boolean musicDucking=true;
    private CheckBox duckingSwitch;
    private int captionStyle = 0, captionAnimation = 0, captionFont = 0, captionSize = 100, captionPosition = 0;
    private boolean timelineTouch = false;
    private LinearLayout captionChips;
    private Spinner styleSpinner, animationSpinner, fontSpinner, positionSpinner;
    private SeekBar fontSizeSlider;
    private TextView fontSizeLabel;
    private TextView timelineLabel;
    private TextView trimLabel, captionsLabel, captionsStatus, previewCaption, fileLabel, musicLabel, status;
    private ProgressBar progress, captionProgress;
    private Button exportBtn, cancelBtn, shareBtn;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        player = new ExoPlayer.Builder(this).build();
        createScreen();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && player.getDuration() > 0) {
                    durationMs = player.getDuration();
                    if (trimEndMs <= 0 || trimEndMs > durationMs) trimEndMs = durationMs;
                    refreshTrims();
                }
            }
        });
        restoreProject();
        handler.post(updatePreview);
    }

    private final Runnable updatePreview = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                if (player != null && videoUri != null) {
                    long t = player.getCurrentPosition();
                    Caption active = null;
                    for (Caption c : captions) if (c.contains(t)) { active = c; break; }
                    long age = active == null ? 0 : Math.max(0, t - active.startMs);
                    previewCaption.setText(active == null ? "" : CaptionStyle.shownText(active.text, captionAnimation, age));
                    updateMediaImage(t);
                    CaptionStyle.preview(previewCaption, captionStyle, captionAnimation,
                            captionFont, captionSize, captionPosition, age, active != null);
                    if (timelineSeek != null && durationMs > 0 && !timelineTouch) {
                        timelineSeek.setProgress((int)Math.min(timelineSeek.getMax(), t / 100));
                        timelineLabel.setText(clock(t) + " / " + clock(durationMs));
                    }
                    // Loop selected range during preview.
                    if (player.isPlaying() && trimEndMs > trimStartMs && t >= trimEndMs) {
                        player.seekTo(trimStartMs);
                    }
                }
                handler.postDelayed(this, 150);
            }
        }
    };

    private void createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = label("EDITORA", 30, LIGHT, true);
        brand.append("AI");
        brand.setLetterSpacing(0.035f);
        root.addView(brand);
        TextView description = label("ESTÚDIO VERTICAL  •  KWAI / SHORTS / REELS", 11, CYAN, true);
        root.addView(description);
        gap(root, 18);

        LinearLayout previewPanel = panel(root);
        FrameLayout previewFrame = new FrameLayout(this);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(370));
        previewPanel.addView(previewFrame, videoParams);
        PlayerView playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setUseController(true);
        previewFrame.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mediaImage = new ImageView(this);
        mediaImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mediaImage.setBackgroundColor(Color.argb(150,8,18,34));
        FrameLayout.LayoutParams mediaParams=new FrameLayout.LayoutParams(-1,dp(222),Gravity.TOP);
        mediaParams.leftMargin=dp(18);mediaParams.rightMargin=dp(18);mediaParams.topMargin=dp(50);
        mediaImage.setVisibility(View.GONE);
        previewFrame.addView(mediaImage,mediaParams);
        previewCaption = label("", 21, Color.WHITE, true);
        previewCaption.setGravity(Gravity.CENTER);
        previewCaption.setShadowLayer(5f, 2f, 2f, Color.BLACK);
        FrameLayout.LayoutParams subp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        subp.leftMargin = dp(14); subp.rightMargin = dp(14); subp.bottomMargin = dp(60);
        previewFrame.addView(previewCaption, subp);
        previewCaption.setVisibility(View.INVISIBLE);
        timelineLabel = label("00:00.0 / 00:00.0", 12, CYAN, true);
        timelineLabel.setGravity(Gravity.CENTER);
        previewPanel.addView(timelineLabel);
        timelineSeek = new SeekBar(this);
        timelineSeek.setMax(1);
        timelineSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        timelineSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int position, boolean fromUser) {
                if (!fromUser || player == null || durationMs <= 0) return;
                long ms = Math.min(durationMs, position * 100L);
                timelineLabel.setText(clock(ms) + " / " + clock(durationMs));
                player.seekTo(ms);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { timelineTouch = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { timelineTouch = false; }
        });
        previewPanel.addView(timelineSeek);
        previewPanel.addView(label("Arraste a linha do tempo para revisar a fala.", 11, MUTED, false));
        fileLabel = label("Nenhum vídeo selecionado", 12, MUTED, false);
        fileLabel.setPadding(0, dp(10), 0, 0);
        previewPanel.addView(fileLabel);
        gap(root, 15);

        LinearLayout sourceBox = panel(root);
        title(sourceBox, "01  ARQUIVOS");
        sourceBox.addView(action("+ Importar vídeo", true, () -> openDocument("video/*", REQ_VIDEO)));
        gap(sourceBox, 8);
        sourceBox.addView(action("♫ Adicionar música de fundo", false, () -> openDocument("audio/*", REQ_MUSIC)));
        musicLabel = label("Sem música extra", 12, MUTED, false);
        musicLabel.setPadding(0, dp(8), 0, 0);
        sourceBox.addView(musicLabel);
        sourceBox.addView(action("Remover música", false, () -> {
            musicUri = null; musicLabel.setText("Sem música extra"); saveProject();
        }));
        gap(sourceBox, 12);
        sourceBox.addView(label("🎚 MIXAGEM INTELIGENTE", 15, CYAN, true));
        sourceBox.addView(label("Controle o volume da sua voz e da trilha, defina quando a música começa e reduza o jazz automaticamente durante a fala. O resultado é aplicado no MP4 exportado.",12,MUTED,false));
        gap(sourceBox, 7);
        voiceVolumeLabel=label("",12,LIGHT,true);
        sourceBox.addView(voiceVolumeLabel);
        voiceVolumeSeek=new SeekBar(this);
        voiceVolumeSeek.setMax(180);voiceVolumeSeek.setProgress(voiceVolumePercent);
        voiceVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress,boolean user){
                voiceVolumePercent=progress;
                updateMixLabels();
                if(user)saveProject();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        sourceBox.addView(voiceVolumeSeek);
        musicVolumeLabel=label("",12,LIGHT,true);
        sourceBox.addView(musicVolumeLabel);
        musicVolumeSeek=new SeekBar(this);
        musicVolumeSeek.setMax(100);musicVolumeSeek.setProgress(musicVolumePercent);
        musicVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress,boolean user){
                musicVolumePercent=progress;
                updateMixLabels();
                if(user)saveProject();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        sourceBox.addView(musicVolumeSeek);
        musicStartLabel=label("",12,LIGHT,true);
        sourceBox.addView(musicStartLabel);
        musicStartSeek=new SeekBar(this);
        musicStartSeek.setMax(600);
        musicStartSeek.setProgress(0);
        musicStartSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress,boolean user){
                musicStartMs=progress*100L;
                updateMixLabels();
                if(user)saveProject();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        sourceBox.addView(musicStartSeek);
        duckingSwitch=new CheckBox(this);
        duckingSwitch.setText("Reduzir música enquanto eu falo (usa legendas)");
        duckingSwitch.setTextColor(LIGHT);
        duckingSwitch.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        duckingSwitch.setChecked(musicDucking);
        duckingSwitch.setOnCheckedChangeListener((button,checked)->{
            musicDucking=checked;updateMixLabels();saveProject();
        });
        sourceBox.addView(duckingSwitch);
        duckVolumeLabel=label("",12,LIGHT,true);
        sourceBox.addView(duckVolumeLabel);
        duckVolumeSeek=new SeekBar(this);
        duckVolumeSeek.setMax(100);duckVolumeSeek.setProgress(duckVolumePercent);
        duckVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress,boolean user){
                duckVolumePercent=progress;updateMixLabels();
                if(user)saveProject();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        sourceBox.addView(duckVolumeSeek);
        sourceBox.addView(label("Dica para a revelação: ajuste a entrada da música para 00:11.0. O ducking usa os intervalos das legendas, portanto gere as legendas offline antes de exportar. Os efeitos de mixagem não são ouvidos na prévia do editor: confira o MP4 exportado.",12,MUTED,false));
        updateMixLabels();
        gap(root, 13);

        LinearLayout trimBox = panel(root);
        title(trimBox, "02  CORTE E FORMATO");
        TextView fixedFormat = label("Formato de exportação: 9:16  •  720 × 1280  •  MP4", 13, LIGHT, true);
        trimBox.addView(fixedFormat);
        trimLabel = label("Importe um vídeo para definir o corte", 12, MUTED, false);
        trimLabel.setPadding(0, dp(10), 0, dp(2));
        trimBox.addView(trimLabel);
        trimBox.addView(label("Início", 12, MUTED, true));
        startSeek = new SeekBar(this); startSeek.setMax(1); startSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        trimBox.addView(startSeek);
        trimBox.addView(label("Fim", 12, MUTED, true));
        endSeek = new SeekBar(this); endSeek.setMax(1); endSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        trimBox.addView(endSeek);
        startSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || durationMs <= 0) return;
                long candidate = Math.min(progress * 100L, durationMs - 500);
                trimStartMs = Math.max(0, Math.min(candidate, Math.max(0, trimEndMs - 500)));
                trimLabel.setText(trimDescription());
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { if (player != null) player.seekTo(trimStartMs); refreshTrims(); saveProject(); }
        });
        endSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || durationMs <= 0) return;
                trimEndMs = Math.min(durationMs, Math.max(trimStartMs + 500, progress * 100L));
                trimLabel.setText(trimDescription());
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { refreshTrims(); saveProject(); }
        });
        gap(root, 13);

        LinearLayout captionsBox = panel(root);
        title(captionsBox, "03  LEGENDAS COM IA");
        captionsBox.addView(label("NOVO • Legendas offline em português! Na primeira vez, baixe o modelo Vosk (~31 MB); depois transcreva vídeos sem API nem internet. A voz não sai do aparelho. Revise possíveis erros de reconhecimento.", 13, MUTED, false));
        gap(captionsBox, 11);
        captionsBox.addView(action("✦ Gerar legendas SEM INTERNET", true, this::requestOfflineTranscription));
        gap(captionsBox, 8);
        captionsBox.addView(action("Usar Groq/Whisper (opcional)", false, this::requestTranscription));
        gap(captionsBox, 8);
        captionsBox.addView(action("▶ Ver legendas na prévia", false, this::previewFirstCaption));
        gap(captionsBox, 8);
        captionsBox.addView(action("Editar legendas", false, this::openCaptionEditor));
        gap(captionsBox, 8);
        captionsBox.addView(action("+ Adicionar legenda manual", false, this::addManualCaption));
        gap(captionsBox, 13);
        captionsBox.addView(label("ESTILO DA LEGENDA", 12, CYAN, true));
        styleSpinner = new Spinner(this);
        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CaptionStyle.STYLES);
        styleSpinner.setAdapter(styleAdapter);
        captionsBox.addView(styleSpinner);
        gap(captionsBox, 8);
        captionsBox.addView(label("ANIMAÇÃO DE ENTRADA", 12, CYAN, true));
        animationSpinner = new Spinner(this);
        ArrayAdapter<String> animationAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CaptionStyle.ANIMATIONS);
        animationSpinner.setAdapter(animationAdapter);
        captionsBox.addView(animationSpinner);
        styleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                captionStyle = CaptionStyle.safeStyle(position);
                saveProject();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        animationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                captionAnimation = CaptionStyle.safeAnimation(position);
                saveProject();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        gap(captionsBox, 12);
        captionsBox.addView(label("FONTE • 12 OPÇÕES", 12, CYAN, true));
        fontSpinner = new Spinner(this);
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CaptionStyle.FONTS);
        fontSpinner.setAdapter(fontAdapter);
        captionsBox.addView(fontSpinner);
        gap(captionsBox, 8);
        captionsBox.addView(action("＋ Importar minha fonte TTF / OTF", false, this::chooseFontFile));
        captionsBox.addView(label("Use fontes que você tenha permissão para utilizar.", 11, MUTED, false));
        fontSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int index, long id) {
                if(index==CaptionStyle.CUSTOM_FONT_INDEX&&!CaptionStyle.customFontFile(MainActivity.this).isFile()){
                    info("Primeiro use o botão Importar minha fonte.");
                    fontSpinner.setSelection(0);return;
                }
                captionFont = CaptionStyle.safeFont(index);
                saveProject();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        gap(captionsBox, 11);
        fontSizeLabel = label("TAMANHO DA FONTE • 100%", 12, CYAN, true);
        captionsBox.addView(fontSizeLabel);
        fontSizeSlider = new SeekBar(this);
        fontSizeSlider.setMax(75);
        fontSizeSlider.setProgress(captionSize - 70);
        fontSizeSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        captionsBox.addView(fontSizeSlider);
        fontSizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser){
                if(!fromUser)return;
                captionSize=CaptionStyle.safeSize(70+value);
                fontSizeLabel.setText("TAMANHO DA FONTE • " + captionSize + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { saveProject(); }
        });
        gap(captionsBox, 10);
        captionsBox.addView(label("POSIÇÃO DAS LEGENDAS", 12, CYAN, true));
        positionSpinner = new Spinner(this);
        ArrayAdapter<String> positions = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CaptionStyle.POSITIONS);
        positionSpinner.setAdapter(positions);
        captionsBox.addView(positionSpinner);
        positionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){
                captionPosition=CaptionStyle.safePosition(index);saveProject();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        gap(captionsBox, 9);
        captionsBox.addView(label("TRECHOS NA LINHA DO TEMPO", 12, CYAN, true));
        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        captionChips = new LinearLayout(this);
        captionChips.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(captionChips);
        captionsBox.addView(chipScroll);
        captionsLabel = label("0 legendas", 12, MUTED, false);
        captionsLabel.setPadding(0, dp(11), 0, 0);
        captionsBox.addView(captionsLabel);
        captionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        captionProgress.setMax(100);
        captionProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams captionProgressLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
        captionProgressLayout.topMargin = dp(12);
        captionsBox.addView(captionProgress, captionProgressLayout);
        captionsStatus = label("Importe um vídeo e toque em gerar legendas. O status vai aparecer aqui.", 13, CYAN, false);
        captionsStatus.setPadding(0, dp(12), 0, 0);
        captionsBox.addView(captionsStatus);
        gap(root, 13);

        LinearLayout analystSection = panel(root);
        title(analystSection, "04  ANALISTA DE IMAGENS, MEMES E SONS");
        analystSection.addView(label("Use as legendas para encontrar momentos do vídeo, pesquisar fotos com licença no Commons e inserir imagens, memes e efeitos sonoros com sua aprovação.",13,MUTED,false));
        gap(analystSection,9);
        analystSection.addView(action("✦ ABRIR ANALISTA DE MÍDIA",true,this::openAnalyst));
        gap(root,13);
        LinearLayout exportBox = panel(root);
        title(exportBox, "05  FINALIZAR");
        exportBox.addView(label("Vídeo vertical com legendas gravadas na imagem. A qualidade depende dos codecs do aparelho.", 13, MUTED, false));
        gap(exportBox, 11);
        exportBtn = action("EXPORTAR VÍDEO 9:16", true, this::startExport);
        exportBox.addView(exportBtn);
        gap(exportBox, 7);
        cancelBtn = action("Cancelar exportação", false, () -> { exporter.cancel(); setBusy(false); info("Exportação cancelada."); });
        cancelBtn.setVisibility(View.GONE);
        exportBox.addView(cancelBtn);
        shareBtn = action("Compartilhar vídeo exportado", false, this::shareVideo);
        shareBtn.setVisibility(View.GONE);
        exportBox.addView(shareBtn);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE);
        exportBox.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        status = label("Pronto para começar.", 12, MUTED, false);
        status.setPadding(0, dp(11), 0, 0);
        exportBox.addView(status);
        gap(root, 17);
        root.addView(label("EditoraAI v0.7 MIXAGEM  •  Volume, entrada e ducking  •  Sem anúncios", 11, MUTED, false));
    }

    private void openDocument(String mime, int code) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, code);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
        if (requestCode == REQ_ANALYST) {
            videoUri=uri;
            try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch(SecurityException ignored){}
            durationMs=0;trimStartMs=0;trimEndMs=0;
            captions.clear();updateCaptionsLabel();
            shownMedia=null;
            activeMedia=MediaCueStore.load(this,uri);
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();player.play();
            fileLabel.setText("Vídeo da nuvem: "+displayName(uri));
            saveProject();
            info("Vídeo da nuvem escolhido. Ajuste o corte, gere legendas e exporte quando quiser.");
            return;
        }
        if (requestCode == REQ_FONT) { importUserFont(uri); return; }
        if (requestCode == REQ_VIDEO) {
            videoUri = uri;
            durationMs = 0; trimStartMs = 0; trimEndMs = 0;
            captions.clear(); updateCaptionsLabel();
            shownMedia=null;
            activeMedia=MediaCueStore.load(this,videoUri);
            if(mediaImage!=null)mediaImage.setVisibility(View.GONE);
            player.setMediaItem(MediaItem.fromUri(videoUri));
            player.prepare();
            player.play();
            fileLabel.setText("Vídeo importado: " + displayName(uri));
            info("Vídeo carregado. Agora ajuste o corte ou gere legendas.");
        } else if (requestCode == REQ_MUSIC) {
            musicUri = uri;
            musicLabel.setText("♫ " + displayName(uri));
            info("Música de fundo adicionada.");
        }
        saveProject();
    }

    private void chooseFontFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,REQ_FONT);
    }
    private void importUserFont(Uri uri){
        String filename=displayName(uri).toLowerCase(Locale.ROOT);
        if(!filename.endsWith(".ttf")&&!filename.endsWith(".otf")){
            error("Selecione uma fonte .ttf ou .otf.");return;
        }
        File temporary=new File(getFilesDir(),"caption_font_pending.bin");
        File target=CaptionStyle.customFontFile(this);
        try(InputStream input=getContentResolver().openInputStream(uri);
            FileOutputStream output=new FileOutputStream(temporary)){
            if(input==null)throw new java.io.IOException("Sem acesso ao arquivo.");
            long total=0;int n;byte[] buffer=new byte[8192];
            while((n=input.read(buffer))!=-1){
                total+=n;
                if(total>6L*1024*1024)throw new java.io.IOException("Fonte maior que 6 MB.");
                output.write(buffer,0,n);
            }
            output.flush();
            Typeface.createFromFile(temporary);
            if(target.exists()&&!target.delete())throw new java.io.IOException("Falha ao substituir a fonte.");
            if(!temporary.renameTo(target))throw new java.io.IOException("Falha ao salvar a fonte.");
            captionFont=CaptionStyle.CUSTOM_FONT_INDEX;
            fontSpinner.setSelection(captionFont);
            saveProject();
            info("Fonte personalizada pronta! Veja na prévia e exporte.");
        }catch(Exception e){ error("Fonte não importada: "+e.getMessage()); }
        finally {if(temporary.exists())temporary.delete();}
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "arquivo";
    }

    /** O download do modelo é opt-in; nada do áudio sai do aparelho na transcrição local. */
    private void requestOfflineTranscription() {
        if (!ensureVideo() || busy) return;
        if (OfflineModelManager.isInstalled(this)) {
            transcribeOffline(false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Baixar o modelo de português?")
                .setMessage("Baixe o modelo oficial de português do Vosk na primeira vez (~31 MB). Depois você poderá legendar sem internet ou chave de API. Recomendamos Wi-Fi. Deseja continuar?")
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Baixar e legendar", (dialog, which) -> transcribeOffline(true))
                .show();
    }

    private void transcribeOffline(boolean installFirst) {
        if (busy) return;
        final Uri requestedVideo = videoUri;
        setBusy(true);
        captionProgress.setVisibility(View.VISIBLE);
        captionProgress.setIndeterminate(installFirst);
        captionProgress.setProgress(0);
        captionStatus("Modelo instalado. Agora vou reconhecer a fala; vídeos longos podem demorar.");
        if (installFirst) captionStatus("Baixando o modelo. Espere o download E a transcrição terminarem.");
        else captionStatus("Lendo e reconhecendo o áudio no próprio celular...");
        work.execute(() -> {
            try {
                if (installFirst) OfflineModelManager.install(getApplicationContext(), (stage, percent) ->
                        handler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            captionProgress.setIndeterminate(false);
                            captionProgress.setProgress(percent);
                            captionStatus(stage + (percent > 0 ? " • " + percent + "%" : ""));
                        }));
                handler.post(() -> {
                    captionProgress.setIndeterminate(false);
                    captionProgress.setProgress(0);
                    captionStatus("Modelo conferido. Agora analisando a voz do vídeo (espere até 100%)...");
                });
                List<Caption> found = OfflineTranscriber.transcribe(getApplicationContext(), requestedVideo, percent -> {
                    if (percent % 5 == 0) handler.post(() -> {
                        captionProgress.setProgress(percent);
                        captionStatus("Reconhecendo áudio: " + percent + "% (ainda não terminou).");
                    });
                });
                handler.post(() -> {
                    // Se foi escolhido outro vídeo, não sobrescrever suas legendas.
                    captionProgress.setVisibility(View.GONE);
                    if (!requestedVideo.equals(videoUri)) {
                        setBusy(false);
                        captionStatus("Você escolheu outro vídeo. As legendas existentes foram preservadas.");
                        return;
                    }
                    if (found.isEmpty()) {
                        setBusy(false);
                        captionStatus("0 legendas: nenhum trecho de fala foi reconhecido.");
                        new AlertDialog.Builder(this).setTitle("Nenhuma legenda encontrada")
                                .setMessage("O modelo terminou, mas não reconheceu palavras. Verifique se o vídeo tem voz audível, sem música muito alta. Você pode usar outro vídeo ou adicionar legendas manualmente.")
                                .setPositiveButton("Entendi", null).show();
                        return;
                    }
                    captions.clear(); captions.addAll(found);
                    sortCaptions(); updateCaptionsLabel(); saveProject(); setBusy(false);
                    previewFirstCaption();
                    captionStatus("✅ " + found.size() + " LEGENDAS CRIADAS! A prévia começou no primeiro trecho. Revise em Editar legendas antes de exportar.");
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                handler.post(() -> {
                    setBusy(false);
                    captionProgress.setVisibility(View.GONE);
                    captionStatus("Não foi possível legendar: " + message);
                    new AlertDialog.Builder(this).setTitle("Falha nas legendas offline")
                            .setMessage(message + "\n\nSe o erro mencionar modelo, confira sua conexão e o espaço livre e tente baixar novamente. Se mencionar áudio ou reconhecimento, tente um vídeo curto com fala clara.")
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void captionStatus(String message) {
        if (captionsStatus != null) captionsStatus.setText(message);
        if (status != null) status.setText(message);
    }

    private void previewFirstCaption() {
        if (captions.isEmpty() || player == null) {
            captionStatus("Ainda não existem legendas. Primeiro gere ou adicione uma legenda.");
            return;
        }
        Caption first = captions.get(0);
        long targetMs = Math.min(Math.max(trimStartMs, first.startMs + 120),
                trimEndMs > 0 ? Math.max(trimStartMs, trimEndMs - 100) : Long.MAX_VALUE);
        player.seekTo(targetMs);
        previewCaption.setText(first.text);
        player.play();
    }

    private void requestTranscription() {
        if (!ensureVideo() || busy) return;
        if (groqKey.isEmpty()) {
            EditText key = new EditText(this);
            key.setSingleLine(true);
            key.setHint("gsk_...");
            key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            LinearLayout wrap = new LinearLayout(this);
            wrap.setPadding(dp(22), 0, dp(22), 0);
            wrap.addView(key, new LinearLayout.LayoutParams(-1, -2));
            new AlertDialog.Builder(this).setTitle("Ativar legendas IA")
                    .setMessage("Cole sua chave pessoal da Groq. Ela não será salva no aparelho. A transcrição envia apenas o áudio à API; podem existir limites ou cobranças do provedor.")
                    .setView(wrap)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Transcrever", (d, w) -> {
                        groqKey = key.getText().toString().trim();
                        if (!groqKey.isEmpty()) transcribe();
                    }).show();
        } else transcribe();
    }

    private void transcribe() {
        if (busy) return;
        setBusy(true);
        info("Extraindo somente a faixa de áudio...");
        work.execute(() -> {
            File audio = null;
            try {
                audio = AudioExtractor.extract(getApplicationContext(), videoUri);
                handler.post(() -> info("Whisper transcrevendo sua fala..."));
                List<Caption> result = GroqTranscriber.transcribe(audio, groqKey);
                handler.post(() -> {
                    captions.clear(); captions.addAll(result);
                    Collections.sort(captions, Comparator.comparingLong(c -> c.startMs));
                    updateCaptionsLabel(); saveProject(); setBusy(false);
                    info("Concluído: " + captions.size() + " trechos de legenda. Revise antes de exportar.");
                });
            } catch (Exception e) {
                String message = e.getMessage();
                handler.post(() -> { setBusy(false); error("Falha na transcrição: " + message); });
            } finally { if (audio != null) audio.delete(); }
        });
    }

    private void openCaptionEditor() {
        if (captions.isEmpty()) { info("Ainda não há legendas. Use a IA ou adicione manualmente."); return; }
        String[] options = new String[captions.size()];
        for (int i = 0; i < captions.size(); i++) {
            Caption c = captions.get(i);
            options[i] = clock(c.startMs) + " - " + clock(c.endMs) + "  " + c.text;
        }
        new AlertDialog.Builder(this).setTitle("Editar trecho da legenda")
                .setItems(options, (dialog, which) -> editCaption(which))
                .setNegativeButton("Fechar", null).show();
    }

    private void editCaption(int index) {
        Caption cap = captions.get(index);
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(dp(20), 0, dp(20), 0);
        fields.setOrientation(LinearLayout.VERTICAL);
        EditText text = editable(cap.text, "Texto"); fields.addView(text);
        EditText start = editable(String.format(Locale.US, "%.2f", cap.startMs / 1000.0), "Início em segundos");
        start.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); fields.addView(start);
        EditText end = editable(String.format(Locale.US, "%.2f", cap.endMs / 1000.0), "Fim em segundos");
        end.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); fields.addView(end);
        new AlertDialog.Builder(this).setTitle("Legenda " + (index + 1)).setView(fields)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Excluir", (d, b) -> { captions.remove(index); updateCaptionsLabel(); saveProject(); })
                .setPositiveButton("Salvar", (d, b) -> {
                    try {
                        long s = parseSeconds(start), e = parseSeconds(end);
                        if (e <= s || text.getText().toString().trim().isEmpty()) throw new Exception();
                        cap.startMs = s; cap.endMs = e; cap.text = text.getText().toString().trim();
                        sortCaptions(); updateCaptionsLabel(); saveProject();
                    } catch (Exception ex) { error("Confira o texto e os segundos inicial/final."); }
                }).show();
    }

    private void addManualCaption() {
        if (!ensureVideo()) return;
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText text = editable("", "Escreva sua legenda..."); fields.addView(text);
        EditText start = editable(String.format(Locale.US, "%.2f", player.getCurrentPosition() / 1000.0), "Início em segundos");
        start.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fields.addView(start);
        EditText end = editable(String.format(Locale.US, "%.2f", (player.getCurrentPosition() + 2000) / 1000.0), "Fim em segundos");
        end.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fields.addView(end);
        new AlertDialog.Builder(this).setTitle("Nova legenda")
                .setView(fields).setNegativeButton("Cancelar", null)
                .setPositiveButton("Adicionar", (d, b) -> {
                    try {
                        long s = parseSeconds(start), e = parseSeconds(end);
                        String words = text.getText().toString().trim();
                        if (words.isEmpty() || e <= s) throw new Exception();
                        captions.add(new Caption(s, e, words));
                        sortCaptions(); updateCaptionsLabel(); saveProject();
                    } catch (Exception ex) { error("Digite texto e um intervalo de tempo válido."); }
                }).show();
    }

    private EditText editable(String value, String hint) {
        EditText input = new EditText(this); input.setText(value); input.setHint(hint);
        input.setTextSize(15); input.setSingleLine(false);
        return input;
    }
    private long parseSeconds(EditText field) {
        return Math.round(Double.parseDouble(field.getText().toString().trim().replace(',', '.')) * 1000);
    }
    private void sortCaptions() { Collections.sort(captions, Comparator.comparingLong(c -> c.startMs)); }
    private void updateCaptionsLabel() {
        if (captionChips != null) {
            captionChips.removeAllViews();
            if (captions.isEmpty()) {
                TextView empty = label("As legendas criadas aparecerão aqui.", 12, MUTED, false);
                captionChips.addView(empty);
            } else {
                for (Caption c : captions) {
                    String shortText = c.text.length() > 32 ? c.text.substring(0, 29) + "…" : c.text;
                    Button chip = action(clock(c.startMs) + "  " + shortText, false, () -> {
                        if (player == null || videoUri == null) return;
                        player.seekTo(Math.max(0, c.startMs));
                        player.play();
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(52));
                    lp.rightMargin = dp(7);
                    captionChips.addView(chip, lp);
                }
            }
        }
        captionsLabel.setText(captions.isEmpty()
                ? "0 legendas geradas — nada será exibido no vídeo ainda."
                : "✅ " + captions.size() + " legendas salvas — toque em ▶ Ver legendas na prévia ou em Editar.");
    }


    private void openAnalyst(){
        if(!ensureVideo())return;
        saveProject();
        if(player!=null)player.pause();
        Intent intent=new Intent(this,AnalystActivity.class);
        intent.putExtra("video",videoUri.toString());
        intent.putExtra("duration",durationMs);
        intent.putExtra("position",player==null?0L:player.getCurrentPosition());
        startActivityForResult(intent,REQ_ANALYST);
    }
    @Override protected void onResume(){
        super.onResume();
        if(videoUri!=null)activeMedia=MediaCueStore.load(this,videoUri);
        shownMedia=null;
    }
    private void updateMediaImage(long currentMs){
        if(mediaImage==null)return;
        MediaCue cue=null;
        for(MediaCue item:activeMedia)if(item.isImage()&&item.contains(currentMs)){cue=item;break;}
        if(shownMedia!=cue){
            Drawable previous=mediaImage.getDrawable();
            if(previous instanceof GifSupport.DrawableGif)
                ((GifSupport.DrawableGif)previous).stop();
            shownMedia=cue;
            if(cue==null){
                mediaImage.setVisibility(View.GONE);mediaImage.setImageDrawable(null);
            }else{
                Uri input=Uri.parse(cue.uri);
                mediaImage.setVisibility(View.VISIBLE);
                if(GifSupport.isGif(this,input)){
                    mediaImage.setImageDrawable(null);
                    final MediaCue expected=cue;
                    work.execute(()->{
                        try{
                            Movie gif=GifSupport.load(getApplicationContext(),input);
                            handler.post(()->{
                                if(mediaImage!=null && shownMedia==expected)
                                    mediaImage.setImageDrawable(new GifSupport.DrawableGif(gif));
                            });
                        }catch(Exception ex){handler.post(()->{
                            if(shownMedia==expected)mediaImage.setImageDrawable(null);
                        });}
                    });
                }else{
                    mediaImage.setImageURI(input);
                }
            }
        }
        if(cue!=null){
            long elapsed=Math.max(0,currentMs-cue.startMs),remaining=Math.max(0,cue.endMs-currentMs);
            float alpha=Math.max(0,Math.min(1f,Math.min(elapsed/180f,remaining/180f)));
            float scale=cue.effect==1?0.84f+0.16f*Math.min(1f,elapsed/240f):1f;
            mediaImage.setAlpha(alpha);mediaImage.setScaleX(scale);mediaImage.setScaleY(scale);
        }
    }

    private void updateMixLabels() {
        if(voiceVolumeLabel!=null)voiceVolumeLabel.setText("🎤 Voz do vídeo: "+voiceVolumePercent+"%");
        if(musicVolumeLabel!=null)musicVolumeLabel.setText("♫ Música de fundo: "+musicVolumePercent+"%");
        if(musicStartLabel!=null)musicStartLabel.setText(String.format(Locale.getDefault(),
                "⏱ Música começa em %02d:%02d.%01d",musicStartMs/60000,
                (musicStartMs/1000)%60,(musicStartMs%1000)/100));
        if(duckVolumeLabel!=null)duckVolumeLabel.setText("Música durante a fala: "+
                duckVolumePercent+"% do volume escolhido"+
                (musicDucking?"":" • redução desligada"));
        if(duckVolumeSeek!=null)duckVolumeSeek.setEnabled(musicDucking);
    }

    private void startExport() {
        if (!ensureVideo() || busy) return;
        if (trimEndMs <= trimStartMs) { error("Selecione um intervalo de vídeo válido."); return; }
        player.pause();
        setBusy(true);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        info("Preparando MP4 com Mixagem Inteligente...");
        if(musicDucking && musicUri!=null && captions.isEmpty()) {
            Toast.makeText(this,"Não há legendas: o volume da música ficará constante. Gere as legendas para baixar durante a fala.",Toast.LENGTH_LONG).show();
        }
        try {
            exporter.start(getApplicationContext(), videoUri, musicUri, trimStartMs, trimEndMs,
                    new ArrayList<>(captions), new ArrayList<>(MediaCueStore.load(this,videoUri)),
                    captionStyle, captionAnimation, captionFont, captionSize, captionPosition,
                    voiceVolumePercent,musicVolumePercent,musicStartMs,musicDucking,duckVolumePercent,
                    new VideoExporter.Listener() {
                        @Override public void success(File mp4) {
                            info("Finalizando o MP4 na galeria...");
                            work.execute(() -> {
                                try {
                                    Uri uri = copyToGallery(mp4);
                                    mp4.delete();
                                    handler.post(() -> {
                                        completedUri = uri;
                                        setBusy(false);
                                        progress.setVisibility(View.GONE);
                                        shareBtn.setVisibility(View.VISIBLE);
                                        info("Vídeo exportado para Filmes/EditoraAI! Pode compartilhar no Kwai.");
                                    });
                                } catch (Exception e) {
                                    handler.post(() -> { setBusy(false); progress.setVisibility(View.GONE); error("Falha ao salvar: " + e.getMessage()); });
                                }
                            });
                        }
                        @Override public void error(String detail) {
                            setBusy(false); progress.setVisibility(View.GONE);
                            error("Erro de exportação: " + detail);
                        }
                    });
            handler.postDelayed(updateExportProgress, 500);
        } catch (Exception e) {
            setBusy(false); progress.setVisibility(View.GONE);
            error("Falha ao preparar exportação: " + e.getMessage());
        }
    }

    private final Runnable updateExportProgress = new Runnable() {
        @Override public void run() {
            if (!busy || progress.getVisibility() != View.VISIBLE) return;
            int p = exporter.progress();
            if (p >= 0) { progress.setProgress(p); info("Renderizando: " + p + "%"); }
            handler.postDelayed(this, 600);
        }
    };

    private Uri copyToGallery(File file) throws Exception {
        ContentValues data = new ContentValues();
        data.put(MediaStore.Video.Media.DISPLAY_NAME, "EditoraAI_" + System.currentTimeMillis() + ".mp4");
        data.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        data.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EditoraAI");
        data.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, data);
        if (uri == null) throw new Exception("Galeria indisponível");
        try (FileInputStream in = new FileInputStream(file); OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("Não foi possível gravar na galeria.");
            byte[] buf = new byte[128 * 1024]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (Exception e) { getContentResolver().delete(uri, null, null); throw e; }
        data.clear(); data.put(MediaStore.Video.Media.IS_PENDING, 0);
        getContentResolver().update(uri, data, null, null);
        return uri;
    }

    private void shareVideo() {
        if (completedUri == null) { info("Exporte o vídeo primeiro."); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("video/mp4");
        send.putExtra(Intent.EXTRA_STREAM, completedUri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Publicar no Kwai ou compartilhar"));
    }

    private void setBusy(boolean value) {
        busy = value;
        exportBtn.setEnabled(!value);
        exportBtn.setAlpha(value ? 0.45f : 1f);
        cancelBtn.setVisibility(value && progress.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }
    private boolean ensureVideo() {
        if (videoUri == null) { error("Importe um vídeo primeiro."); return false; }
        return true;
    }
    private void info(String message) { status.setText(message); }
    private void error(String message) { info(message); Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private String trimDescription() { return "De " + clock(trimStartMs) + " até " + clock(trimEndMs) + "  (" + clock(Math.max(0, trimEndMs - trimStartMs)) + ")"; }
    private static String clock(long ms) { return String.format(Locale.getDefault(), "%02d:%02d.%01d", ms/60000, (ms/1000)%60, (ms%1000)/100); }
    private void refreshTrims() {
        if (durationMs <= 0) return;
        if (timelineSeek != null) timelineSeek.setMax((int) Math.max(1, durationMs / 100));
        if (musicStartSeek != null) {
            musicStartSeek.setMax((int)Math.max(1,Math.min(durationMs,60*60*1000L)/100L));
            if(musicStartMs>durationMs)musicStartMs=0;
            musicStartSeek.setProgress((int)(musicStartMs/100L));
            updateMixLabels();
        }
        startSeek.setMax((int) Math.max(1, durationMs/100));
        endSeek.setMax((int) Math.max(1, durationMs/100));
        startSeek.setProgress((int) (trimStartMs/100));
        endSeek.setProgress((int) (trimEndMs/100));
        trimLabel.setText(trimDescription());
    }

    private void saveProject() {
        SharedPreferences.Editor e = getSharedPreferences("editor", MODE_PRIVATE).edit();
        e.putString("video", videoUri == null ? "" : videoUri.toString());
        e.putString("music", musicUri == null ? "" : musicUri.toString());
        e.putLong("start", trimStartMs); e.putLong("end", trimEndMs);
        e.putInt("voice_gain",voiceVolumePercent);
        e.putInt("music_gain",musicVolumePercent);
        e.putLong("music_delay",musicStartMs);
        e.putBoolean("auto_duck",musicDucking);
        e.putInt("duck_gain",duckVolumePercent);
        e.putInt("caption_style", captionStyle); e.putInt("caption_animation", captionAnimation);
        e.putInt("caption_font", captionFont); e.putInt("caption_size", captionSize);
        e.putInt("caption_position", captionPosition);
        JSONArray arr = new JSONArray(); for (Caption c : captions) arr.put(c.toJson());
        e.putString("captions", arr.toString()); e.apply();
    }
    private void restoreProject() {
        SharedPreferences p = getSharedPreferences("editor", MODE_PRIVATE);
        try {
            voiceVolumePercent=Math.max(0,Math.min(180,p.getInt("voice_gain",100)));
            musicVolumePercent=Math.max(0,Math.min(100,p.getInt("music_gain",26)));
            musicStartMs=Math.max(0,p.getLong("music_delay",0));
            musicDucking=p.getBoolean("auto_duck",true);
            duckVolumePercent=Math.max(0,Math.min(100,p.getInt("duck_gain",28)));
            if(voiceVolumeSeek!=null)voiceVolumeSeek.setProgress(voiceVolumePercent);
            if(musicVolumeSeek!=null)musicVolumeSeek.setProgress(musicVolumePercent);
            if(musicStartSeek!=null)musicStartSeek.setProgress((int)Math.min(musicStartSeek.getMax(),musicStartMs/100L));
            if(duckingSwitch!=null)duckingSwitch.setChecked(musicDucking);
            if(duckVolumeSeek!=null)duckVolumeSeek.setProgress(duckVolumePercent);
            updateMixLabels();
            captionStyle = CaptionStyle.safeStyle(p.getInt("caption_style", 0));
            captionAnimation = CaptionStyle.safeAnimation(p.getInt("caption_animation", 0));
            captionFont = CaptionStyle.safeFont(p.getInt("caption_font", 0));
            if(captionFont==CaptionStyle.CUSTOM_FONT_INDEX
                &&!CaptionStyle.customFontFile(this).isFile())captionFont=0;
            captionSize = CaptionStyle.safeSize(p.getInt("caption_size", 100));
            captionPosition = CaptionStyle.safePosition(p.getInt("caption_position", 0));
            if (styleSpinner != null) styleSpinner.setSelection(captionStyle);
            if (animationSpinner != null) animationSpinner.setSelection(captionAnimation);
            if (fontSpinner != null) fontSpinner.setSelection(captionFont);
            if (positionSpinner != null) positionSpinner.setSelection(captionPosition);
            if (fontSizeSlider != null) fontSizeSlider.setProgress(captionSize - 70);
            if (fontSizeLabel != null) fontSizeLabel.setText("TAMANHO DA FONTE • " + captionSize + "%");
            String v = p.getString("video", "");
            if (!v.isEmpty()) {
                videoUri = Uri.parse(v); trimStartMs = p.getLong("start", 0); trimEndMs = p.getLong("end", 0);
                player.setMediaItem(MediaItem.fromUri(videoUri)); player.prepare();
                fileLabel.setText("Projeto recuperado: " + displayName(videoUri));
            }
            String m = p.getString("music", "");
            if (!m.isEmpty()) { musicUri = Uri.parse(m); musicLabel.setText("♫ " + displayName(musicUri)); }
            JSONArray arr = new JSONArray(p.getString("captions", "[]"));
            for (int i = 0; i < arr.length(); i++) captions.add(Caption.fromJson(arr.getJSONObject(i)));
            updateCaptionsLabel();
        } catch (Exception ignored) { info("Não foi possível recuperar o projeto anterior."); }
    }

    private LinearLayout panel(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(15), dp(14), dp(15));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(PANEL); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), STROKE);
        box.setBackground(bg);
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }
    private void title(LinearLayout parent, String value) {
        TextView tv = label(value, 14, LIGHT, true);
        tv.setPadding(0, 0, 0, dp(11)); parent.addView(tv);
    }
    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button action(String caption, boolean primary, Runnable onClick) {
        Button b = new Button(this);
        b.setText(caption); b.setAllCaps(false);
        b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : LIGHT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? CYAN : Color.rgb(33, 48, 71)); bg.setCornerRadius(dp(12));
        if (!primary) bg.setStroke(dp(1), STROKE);
        b.setBackground(bg);
        b.setMinHeight(dp(48)); b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }
    private void gap(LinearLayout l, int height) {
        View view = new View(this); l.addView(view, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        if (busy) exporter.cancel();
        work.shutdownNow();
        super.onDestroy();
    }
}
