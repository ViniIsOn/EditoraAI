package com.vini.editoraai;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Controles de volume reais durante o processamento PCM do Media3.
 * A redução da trilha usa intervalos da fala transcrita (Vosk); não finge reconhecer
 * voz se as legendas estiverem vazias. Processadores nunca são compartilhados entre faixas.
 */
@UnstableApi
public final class SmartMixAudioProcessor extends BaseAudioProcessor {
    private final float normalGain;
    private final float speakingGain;
    private final boolean autoDuck;
    private final long outputOffsetMs;
    private final long originalTrimStartMs;
    private final List<long[]> speech = new ArrayList<>();
    private final int fadeMs;
    private long frames;
    private float smoothGain;
    private final boolean applyFadeIn;

    public SmartMixAudioProcessor(float normalGain, float speakingGain, boolean autoDuck,
            long outputOffsetMs, long originalTrimStartMs, List<Caption> captions,
            boolean applyFadeIn) {
        this.normalGain = Math.max(0f, Math.min(2f, normalGain));
        this.speakingGain = Math.max(0f, Math.min(2f, speakingGain));
        this.autoDuck = autoDuck && captions != null && !captions.isEmpty();
        this.outputOffsetMs = Math.max(0, outputOffsetMs);
        this.originalTrimStartMs = originalTrimStartMs;
        this.applyFadeIn = applyFadeIn;
        fadeMs=260;
        if(captions != null)for(Caption c:captions) {
            if(c.endMs>c.startMs) speech.add(new long[]{c.startMs,c.endMs});
        }
        smoothGain=this.normalGain;
    }

    @Override protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat input)
            throws AudioProcessor.UnhandledAudioFormatException {
        if(input.encoding != C.ENCODING_PCM_16BIT)
            throw new AudioProcessor.UnhandledAudioFormatException(
                    "Mixagem Inteligente requer PCM16",input);
        return input;
    }

    @Override protected void onFlush() {
        frames=0;
        smoothGain=normalGain;
    }

    @Override public void queueInput(ByteBuffer input) {
        int bytes=input.remaining();
        int complete=bytes-(bytes%2);
        ByteBuffer output=replaceOutputBuffer(complete).order(ByteOrder.nativeOrder());
        final int channels=Math.max(1,inputAudioFormat.channelCount);
        final int rate=Math.max(1,inputAudioFormat.sampleRate);
        int samples=complete/2;
        long groupSample=0;
        float gain=smoothGain;
        for(int s=0;s<samples;s++){
            if(s%channels==0){
                long atOutputMs=outputOffsetMs + (frames*1000L / rate);
                long atSourceMs=originalTrimStartMs+atOutputMs;
                boolean speechNow=false;
                if(autoDuck){
                    // Starts ducking shortly before a caption; recover shortly after it ends.
                    for(long[] span:speech){
                        if(atSourceMs+120>=span[0] && atSourceMs< span[1]+220){
                            speechNow=true;break;
                        }
                    }
                }
                float target=speechNow?speakingGain:normalGain;
                // Exponential smoothing avoids pops between spoken and silent phrases.
                float step=1f-Math.min(0.9999f,
                        (float)Math.exp(-1f/(rate*0.13f)));
                gain+=(target-gain)*step;
                if(applyFadeIn && atOutputMs<outputOffsetMs+fadeMs){
                    float entry=Math.max(0f,Math.min(1f,(atOutputMs-outputOffsetMs)/(float)fadeMs));
                    groupSample=gain<=0 ? 0 : Float.floatToIntBits(gain*entry);
                }else groupSample=Float.floatToIntBits(gain);
                frames++;
            }
            float actualGain=Float.intBitsToFloat((int)groupSample);
            int value=Math.round(input.getShort()*actualGain);
            output.putShort((short)Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE,value)));
        }
        smoothGain=gain;
        output.flip();
    }
}
