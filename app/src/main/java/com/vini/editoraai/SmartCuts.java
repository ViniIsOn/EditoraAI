package com.vini.editoraai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Sugestões reversíveis a partir dos TEMPOS das legendas revisadas.
 * Sem detecção de fala independente: som ambiente ou reconhecimento ruim podem gerar falsas pausas.
 */
public final class SmartCuts {
    private SmartCuts(){}
    public static final class Cut {
        public final long startMs,endMs;
        public final String reason;
        public Cut(long startMs,long endMs,String reason){
            this.startMs=startMs;this.endMs=endMs;this.reason=reason;
        }
    }
    public static final class Span {
        public final long startMs,endMs;
        public Span(long start,long end){startMs=start;endMs=end;}
        public long length(){return endMs-startMs;}
    }
    public static boolean fillerOnly(String raw) {
        if(raw==null)return false;
        String normalized=java.text.Normalizer.normalize(raw.toLowerCase(Locale.ROOT),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .replaceAll("[^a-z ]"," ").trim().replaceAll("\\s+"," ");
        return normalized.matches("(e+|eh+|ahn+|ahm+|hum+|hmm+|hahn+|err+|hm+)");
    }
    /**
     * Um intervalo "sem legendas" só vira candidato se for grande e não esbarrar
     * na fala reconhecida. Remove excesso, preservando 180ms a cada lado.
     */
    public static List<Cut> propose(List<Caption> captions,long start,long end,
                                     int gapThresholdMs,boolean fillers){
        ArrayList<Cut> list=new ArrayList<>();
        if(end-start<1300)return list;
        ArrayList<Caption> sorted=new ArrayList<>();
        for(Caption c:captions)if(c.endMs>start&&c.startMs<end)sorted.add(c);
        sorted.sort(Comparator.comparingLong(c->c.startMs));
        if(sorted.isEmpty())return list; // nunca apaga o vídeo todo se não reconheceu voz
        long cursor=start;
        for(Caption c:sorted){
            long a=Math.max(start,c.startMs),b=Math.min(end,c.endMs);
            if(a>cursor && a-cursor>=Math.max(1500,gapThresholdMs)){
                // Margem maior: não encostar em sílabas/frases próximas ao intervalo.
                long lo=cursor==start?cursor:cursor+400;
                long hi=a-400;
                if(hi-lo>=450)list.add(new Cut(lo,hi,"Possível pausa (confirme ouvindo)"));
            }
            if(fillers && fillerOnly(c.text) && b-a>=240
                    && a>start+250 && b<end-250){
                list.add(new Cut(a,b,"Hesitação: "+c.text));
            }
            cursor=Math.max(cursor,b);
        }
        if(end-cursor>=gapThresholdMs){
            long lo=cursor+400;
            long hi=end-180;
            if(hi-lo>=450)list.add(new Cut(lo,hi,"Possível pausa final"));
        }
        list.sort(Comparator.comparingLong(c->c.startMs));
        return list;
    }
    public static List<Span> kept(long start,long end,List<Cut> selected){
        ArrayList<Span> keep=new ArrayList<>();
        if(end<=start)return keep;
        ArrayList<Cut> sorted=new ArrayList<>(selected);
        sorted.sort(Comparator.comparingLong(c->c.startMs));
        long cursor=start;
        for(Cut cut:sorted){
            long a=Math.max(start,Math.min(end,cut.startMs));
            long b=Math.max(a,Math.min(end,cut.endMs));
            if(a>cursor && a-cursor>=160)keep.add(new Span(cursor,a));
            cursor=Math.max(cursor,b);
        }
        if(end-cursor>=160)keep.add(new Span(cursor,end));
        return keep;
    }
    public static long duration(List<Span> spans){
        long total=0;
        for(Span span:spans)total+=span.length();
        return total;
    }
    /** Mapeia um instante da fonte para o MP4 cortado. Negativo se apagado. */
    public static long toOutput(long sourceMs,List<Span> kept){
        long base=0;
        for(Span span:kept) {
            if(sourceMs>=span.startMs&&sourceMs<span.endMs)return base+sourceMs-span.startMs;
            base+=span.length();
        }
        return -1;
    }
    /** Uma legenda pode atravessar um corte: cria uma parte para cada segmento preservado. */
    public static List<Caption> mapCaptions(List<Caption> original,List<Span> kept){
        List<Caption> out=new ArrayList<>();
        long base=0;
        for(Span span:kept){
            for(Caption c:original){
                long a=Math.max(c.startMs,span.startMs);
                long b=Math.min(c.endMs,span.endMs);
                if(b>a && !c.text.trim().isEmpty())
                    out.add(new Caption(base+a-span.startMs,base+b-span.startMs,c.text));
            }
            base+=span.length();
        }
        return out;
    }
    /** Posições de junção no MP4, excluindo bordas. */
    public static List<Long> joinPositions(List<Span> kept){
        List<Long> joins=new ArrayList<>();
        long position=0;
        for(int i=0;i<kept.size();i++){
            position+=kept.get(i).length();
            if(i+1<kept.size())joins.add(position);
        }
        return joins;
    }
    /** Mantém os itens de apoio após cortes, inclusive se atravessarem uma junção. */
    public static List<MediaCue> mapCues(List<MediaCue> cues,List<Span> kept){
        List<MediaCue> result=new ArrayList<>();
        long offset=0;
        for(Span span:kept){
            for(MediaCue cue:cues){
                if(!cue.isImage()&&!"sfx".equals(cue.kind))continue;
                long a=Math.max(span.startMs,cue.startMs);
                long b=Math.min(span.endMs,cue.endMs);
                if(b<=a)continue;
                result.add(new MediaCue(cue.kind,cue.uri,cue.label,cue.credit,cue.sound,
                        offset+a-span.startMs,offset+b-span.startMs,cue.effect));
            }
            offset+=span.length();
        }
        return result;
    }

    /** SFX são atrelados à parte audível da fonte, sem mudar outros itens. */
    public static List<MediaCue> mapSoundCues(List<MediaCue> cues,List<Span> kept){
        List<MediaCue> out=new ArrayList<>();
        long base=0;
        for(Span span:kept){
            for(MediaCue c:cues)if("sfx".equals(c.kind)){
                long a=Math.max(c.startMs,span.startMs);
                long b=Math.min(c.endMs,span.endMs);
                if(b>a){
                    out.add(new MediaCue(c.kind,c.uri,c.label,c.credit,c.sound,
                            base+a-span.startMs,base+b-span.startMs,c.effect));
                }
            }
            base+=span.length();
        }
        return out;
    }
}
