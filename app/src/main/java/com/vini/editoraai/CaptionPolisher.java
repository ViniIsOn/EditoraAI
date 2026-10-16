package com.vini.editoraai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Pós-processamento local da transcrição. Não promete corrigir áudio não reconhecido. */
public final class CaptionPolisher {
    private CaptionPolisher(){}
    public static final String DEFAULT_GLOSSARY =
            "sonic = Sonic\nrumble = Rumble\nvector = Vector\n"
            +"eggman = Eggman\nkwai = Kwai\nchaotix = Chaotix\n"
            +"poder azul = Poder Azul\nnew donk city = New Donk City";

    private static String normalize(String text,String glossary){
        String result=text==null?"":text.trim().replaceAll("\\s+"," ");
        if(result.isEmpty())return result;
        if(glossary!=null){
            for(String line:glossary.split("\n")){
                int index=line.indexOf('=');
                if(index<1)continue;
                String wrong=line.substring(0,index).trim();
                String right=line.substring(index+1).trim();
                if(wrong.isEmpty()||right.isEmpty()||wrong.length()>80||right.length()>80)continue;
                try{
                    Pattern pattern=Pattern.compile("(?<!\\p{L})"+
                            Pattern.quote(wrong)+"(?!\\p{L})",
                            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
                    Matcher matcher=pattern.matcher(result);
                    result=matcher.replaceAll(Matcher.quoteReplacement(right));
                }catch(Exception ignored){}
            }
        }
        if(!result.isEmpty()){
            String first=result.substring(0,1).toUpperCase(new Locale("pt","BR"));
            result=first+result.substring(1);
        }
        return result;
    }
    public static List<Caption> polish(List<Caption> original,String glossary){
        ArrayList<Caption> updated=new ArrayList<>();
        for(int i=0;i<original.size();i++){
            Caption c=original.get(i);
            String message=normalize(c.text,glossary);
            if(message.isEmpty())continue;
            // Pontuação básica em pausas claras, sem reescrever o que foi dito.
            long next=i+1<original.size()?original.get(i+1).startMs:Long.MAX_VALUE;
            if(next-c.endMs>850 && !message.matches(".*[.!?…]$")){
                message+=".";
            }
            updated.add(new Caption(c.startMs,c.endMs,message));
        }
        return updated;
    }
}
