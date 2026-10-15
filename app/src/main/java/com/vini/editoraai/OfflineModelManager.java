package com.vini.editoraai;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Instala com consentimento um modelo Vosk PT-BR; depois só utiliza dados locais. */
public final class OfflineModelManager {
    public interface Progress { void report(String stage, int percent); }

    private static final String NAME = "vosk-model-small-pt-0.3";
    private static final String SOURCE = "https://alphacephei.com/vosk/models/" + NAME + ".zip";
    private static final long MAX_ZIP_BYTES = 85L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED = 380L * 1024 * 1024;
    private OfflineModelManager() { }

    public static File modelDir(Context context) { return new File(context.getFilesDir(), NAME); }
    public static boolean isInstalled(Context context) { return valid(modelDir(context)); }
    /**
     * Vosk aceita dois formatos de modelo:
     * - v1/legado: arquivos diretamente na raiz (português small-pt-0.3);
     * - v2: pastas am/, conf/ e graph/.
     *
     * O app anterior validava somente v2/HCLG.fst, rejeitando incorretamente
     * o modelo oficial português v1, que usa final.mdl + mfcc.conf na raiz
     * e o par HCLr.fst/Gr.fst. O próprio construtor nativo Vosk suporta v1.
     */
    private static boolean valid(File d) {
        boolean legacy = nonEmpty(d, "final.mdl")
                && nonEmpty(d, "mfcc.conf")
                && (nonEmpty(d, "HCLG.fst")
                    || (nonEmpty(d, "HCLr.fst") && nonEmpty(d, "Gr.fst")));
        boolean current = nonEmpty(d, "am/final.mdl")
                && nonEmpty(d, "conf/model.conf")
                && nonEmpty(d, "conf/mfcc.conf")
                && (nonEmpty(d, "graph/HCLG.fst")
                    || (nonEmpty(d, "graph/HCLr.fst") && nonEmpty(d, "graph/Gr.fst")));
        return d.isDirectory() && (legacy || current);
    }

    private static boolean nonEmpty(File root, String relativePath) {
        File file = new File(root, relativePath);
        return file.isFile() && file.length() > 0L;
    }

    public static void install(Context context, Progress progress) throws IOException {
        if (isInstalled(context)) return;
        File zip = new File(context.getCacheDir(), NAME + ".download");
        File staging = new File(context.getFilesDir(), NAME + ".installing");
        if (staging.exists()) remove(staging);
        if (!staging.mkdirs()) throw new IOException("Sem espaço para preparar o modelo de voz.");
        try {
            progress.report("Baixando modelo PT-BR (uma vez só)", 0);
            HttpURLConnection connection = (HttpURLConnection) new URL(SOURCE).openConnection();
            try {
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(45000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (connection.getResponseCode() != 200)
                    throw new IOException("Servidor do modelo respondeu HTTP " + connection.getResponseCode());
                long expected = connection.getContentLengthLong();
                if (expected > MAX_ZIP_BYTES) throw new IOException("Download maior que o permitido.");
                long readTotal = 0;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(zip))) {
                    byte[] buffer = new byte[64 * 1024];
                    int n, oldPct = -1;
                    while ((n = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Download interrompido.");
                        readTotal += n;
                        if (readTotal > MAX_ZIP_BYTES) throw new IOException("Arquivo muito grande.");
                        output.write(buffer, 0, n);
                        int pct = expected > 0 ? (int) Math.min(100, 100 * readTotal / expected) : 0;
                        if (pct != oldPct) { progress.report("Baixando modelo PT-BR", pct); oldPct = pct; }
                    }
                }
                if (expected > 0 && readTotal != expected) throw new IOException("O download veio incompleto.");
            } finally { connection.disconnect(); }

            progress.report("Preparando reconhecimento offline", 0);
            long unpackedBytes = 0;
            int count = 0;
            try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Instalação interrompida.");
                    if (++count > 4000) throw new IOException("Modelo com arquivos demais.");
                    String path = entry.getName().replace('\\', '/');
                    // O zip oficial contém uma pasta-pai igual ao nome do modelo.
                    String prefix = NAME + "/";
                    if (path.equals(NAME) || path.equals(prefix)) continue;
                    if (!path.startsWith(prefix)) throw new IOException("Estrutura inesperada do modelo.");
                    String relative = path.substring(prefix.length());
                    if (relative.isEmpty()) continue;
                    File destination = new File(staging, relative);
                    String root = staging.getCanonicalPath() + File.separator;
                    if (!destination.getCanonicalPath().startsWith(root)) throw new IOException("Caminho inválido no modelo.");
                    if (entry.isDirectory()) {
                        if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Não foi possível criar a pasta do modelo.");
                    } else {
                        File parent = destination.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Sem espaço para o modelo.");
                        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                            byte[] buffer = new byte[32 * 1024];
                            int n;
                            while ((n = input.read(buffer)) != -1) {
                                unpackedBytes += n;
                                if (unpackedBytes > MAX_UNCOMPRESSED) throw new IOException("Modelo expandido acima do limite.");
                                output.write(buffer, 0, n);
                            }
                        }
                    }
                    input.closeEntry();
                }
            }
            if (!valid(staging)) throw new IOException(
                    "O pacote baixado está incompleto ou com estrutura desconhecida. "
                    + "O EditoraAI agora aceita modelos Vosk v1 e v2, mas "
                    + "ainda não encontrou os arquivos fundamentais; toque novamente para baixar.");
            File installed = modelDir(context);
            if (installed.exists()) remove(installed);
            if (!staging.renameTo(installed)) throw new IOException("Não foi possível concluir a instalação do modelo.");
            progress.report("Modelo de português conferido e instalado: transcrição offline liberada", 100);
        } finally {
            zip.delete();
            if (staging.exists()) remove(staging);
        }
    }

    private static void remove(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) remove(child);
        }
        if (file.exists() && !file.delete()) throw new IOException("Não foi possível limpar arquivos temporários.");
    }
}
