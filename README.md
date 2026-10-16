# EditoraAI v1.1 — Correções dos cortes e efeitos anime

Esta versão responde a bugs confirmados num MP4 exportado de 44,8 s na v1.0: o vídeo tinha cortes ruins, legendas fora de sincronia e o efeito visual não correspondia aos triângulos de ação esperados.

## Correções de exportação

- **Cortes em duas etapas**: com dois ou mais clipes, a primeira etapa produz um vídeo intermediário limpo com imagem e áudio concatenados (sem música/FX/legendas). A segunda aplica legendas, música, marca d'água e efeitos sobre o **único vídeo já montado**, remapeando os tempos. Não há sobreposições com horários misturados em dezenas de clipes.
- Quando **nenhum corte está selecionado**, exporta diretamente em uma etapa e mantém toda a gravação.
- O áudio original sempre é preservado no arquivo; o compositor apenas adiciona a trilha musical se o usuário a escolher.
- O vídeo intermediário é removido depois da exportação e ao cancelar.

## Cortes com revisão conservadora

- Nenhum corte é pré-selecionado. Sem sua confirmação, a gravação inteira fica intacta.
- Só sugere pausas longas (cerca de 1,8 s ou mais) com margem de segurança antes/depois das palavras reconhecidas.
- Hesitações isoladas como "ééé" ou "ahn" podem aparecer como sugestão, mas nunca são removidas automaticamente.
- Até 11 cortes escolhidos por exportação. Você pode desfazer todas as seleções antes de exportar.
- A detecção ainda usa **horários do Vosk**, não um detector físico/infalível de silêncio; se a legenda estiver errada, o intervalo pode estar errado. Ouça e confira os horários!

## Efeito de anime revisado

Modo 1: **✧ Partículas BRANCAS triangulares (anime)**. Fragmentos brancos em movimento sobre o vídeo, com fundo totalmente transparente. Outros modos são linhas de ação brancas, impacto circular com triângulos e flash branco suave, também sem fundo azul. Os efeitos aparecem no início do vídeo e nos pontos de junção dos cortes, ou aproximadamente a cada 8 segundos quando não há cortes.

São animações de ação, **não** transições reais de fusão de cenas.

## Legendas e roteiro

Mantém o dicionário editável de nomes para Sonic, Rumble, Vector, Eggman, Poder Azul etc. O motor Vosk offline não mudou, e pode reconhecer algumas palavras erradas; revise as legendas antes dos cortes. No teleprompter, selecione o roteiro Sonic Rumble ou Poder Azul e use Próxima fala para saltar o texto.

## Como testar esta correção

1. Primeiro, importe um vídeo de 10–20s e **não selecione nenhum corte**; marque Partículas BRANCAS e exporte. Compare áudio e legenda com a gravação original.
2. Em outro teste, gere legendas, selecione só UMA pausa longa claramente sem fala, exporte e confira continuidade e sincronização.
3. Só depois aplique vários cortes. Se algum corte ficar incorreto, remova aquela seleção; o vídeo original não é alterado.
4. Para conteúdo já cortado com erro, importe novamente a **gravação original**. As sílabas já removidas do MP4 antigo não podem ser recuperadas dele.

## Compatibilidade

Debug APK separado (applicationId `com.vini.editoraai.animerefine`) para preservar todos os projetos das versões anteriores. Ao ser um pacote separado, o modelo Vosk precisará ser instalado novamente para legendas offline.

GitHub Actions: JDK17, Android SDK35, CameraX 1.4.2, Media3 1.8.0. **Compilação não substitui teste real de exportação**: por isso a orientação de testar em vídeo curto antes de editar um trabalho longo.
