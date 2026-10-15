package com.vini.editoraai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Análise local POR PALAVRAS-CHAVE, não LLM; não inventa cenas nem baixa material
 * sem a autorização da pessoa que edita.
 */
public final class ScriptAnalyst {
    public static final class Idea {
        public final String query, why;
        public final long startMs,endMs;
        public Idea(String query,String why,long start,long end) {
            this.query=query;this.why=why;this.startMs=start;this.endMs=end;
        }
    }
    private static final Set<String> STOP=new HashSet<>();
    static {
        String words="que para hoje voce vocês onde quando porque sobre isso esse essa como aqui"
           +" estou estamos falando muito pouco tambem também tipo gente pessoal video vídeo vamos"
           +" agora então cara mano olha olha só nosso minha meu seus suas quero seria esta foi vai"
           +" pelo pela mesmo ainda mais menos tinha tem uma umas uns os as de da do dos das em na"
           +" no nos nas por com sem ou e o a um uma se eu tu ele ela nós eles elas é são era";
        for(String w:words.split(" "))STOP.add(normalize(w));
    }
    private ScriptAnalyst(){}
    public static List<Idea> analyze(List<Caption> captions,long videoDurationMs) {
        List<Idea> out=new ArrayList<>();
        Set<String> used=new HashSet<>();
        for(Caption caption:captions) {
            if(out.size()>=12)break;
            String norm=normalize(caption.text);
            String query=null;
            if(norm.contains("sonic rumble"))query="Sonic Rumble";
            else if(norm.contains("sonic"))query="Sonic the Hedgehog game";
            else if(norm.contains("roblox"))query="Roblox video game";
            else if(norm.contains("free fire"))query="Free Fire mobile game";
            else if(norm.contains("minecraft"))query="Minecraft game";
            else if(norm.contains("natureza"))query="Natureza";
            else if(norm.contains("arara"))query="Arara azul";
            else if(norm.contains("jogo"))query="Video game";
            if(query==null){
                String[] parts=norm.replaceAll("[^\\p{L}\\p{Nd} ]"," ").split("\\s+");
                for(String word:parts){
                    if(word.length()>=5&&!STOP.contains(word)&&!used.contains(word)){
                        query=word;break;
                    }
                }
            }
            if(query==null||query.trim().isEmpty())continue;
            String key=normalize(query);
            if(!used.add(key))continue;
            long start=Math.max(0,caption.startMs);
            long end=Math.min(videoDurationMs>0?videoDurationMs:Long.MAX_VALUE,
                    Math.max(start+1500,Math.min(caption.endMs+500,start+4000)));
            out.add(new Idea(query,"Sua fala: “"+caption.text+"”",start,end));
        }
        return out;
    }
    private static String normalize(String input){
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","");
    }
}
