# EditoraAI v1.0 — SMART EDIT (Pausas, FX anime, legendas e roteiro Sonic)

## O que esta versão faz de verdade

Continua tudo que existia na v0.9: câmera frontal com teleprompter, mixagem de voz/música, Vosk PT-BR, edições de legendas, marca d'água personalizada PNG, GIFs, imagens e efeitos sonoros.

### 1. Cortes inteligentes com confirmação

1. Importe ou grave o vídeo.
2. Gere as **legendas offline** no painel Legendas com IA. Revise e corrija erros.
3. Em **Cortes Inteligentes**, toque **Detectar pausas e revisar cortes**.
4. O app detecta intervalos **sem palavras reconhecidas** com aproximadamente 0,9 segundo ou mais e hesitações isoladas como "ahn", "eh" e "hmm" quando reconhecidas pelo Vosk.
5. Confira horários das sugestões (o botão Ouvir vídeo inicia na primeira sugestão), marque/desmarque e toque Aplicar seleção.
6. Na exportação, o app **monta uma sequência de trechos preservados**, com jump cuts, e adapta a linha do tempo de legendas, música e efeitos sonoros. As imagens e legendas são renderizadas usando os tempos originais em cada clipe.
7. **Desfazer todos os cortes** apaga a seleção, não o arquivo original.

Limites: até 15 cortes aceitos, para evitar excesso de memória/custo de renderização. Os cortes são baseados nas palavras que o Vosk reconheceu, **não são um detector acústico de silêncio**. Um ruído ou uma palavra mal reconhecida pode causar corte incorreto. Revisão manual é indispensável. Hesitações dentro de frases completas podem não ser detectadas. A prévia continua mostrando o vídeo original; somente o MP4 final é remontado.

### 2. FX anime e transições gráficas

Escolha **Flash anime**, **Linhas de velocidade**, **Glitch colorido** ou **Explosão pop**. As animações aparecem brevemente nas entradas dos segmentos cortados e em acentos visuais a cada aproximadamente 9 segundos. Esses efeitos são sobreposições visuais sobre o vídeo, não um dissolve/crossfade entre gravações nem montagem de múltiplas câmeras.

### 3. Legendas com dicionário personalizado

- Transcrição ainda usa o modelo local Vosk (~31 MB). Segmentação de frases passou de 7 para até 9 palavras, com pausas um pouco mais naturais.
- O pós-processamento inclui pontuação em pausas maiores e um dicionário editável de substituições, com nomes como **Sonic, Rumble, Vector, Eggman, Poder Azul**.
- Abra **Corrigir nomes: Sonic, Vector, Poder Azul...**, inclua linhas no formato `palavra reconhecida = palavra correta` e salve.
- O reconhecimento acústico não virou um modelo de IA superior: nomes e frases ainda podem ser reconhecidos incorretamente, e o usuário deve revisar as legendas antes de aplicar cortes.

### 4. Teleprompter sem esperar o texto antigo

O gravador começa com o roteiro vazio na instalação inicial. Toque **Escolher roteiro pronto** e selecione **Curiosidades Sonic Rumble**, **Revelação Poder Azul** ou **Em branco**. O botão **Próxima fala** avança para o próximo parágrafo imediatamente, sem esperar a rolagem; funciona mesmo enquanto grava. Você também pode editar/colar qualquer texto. A gravação não mostra o texto.

## Instalação

APK debug tem applicationId **com.vini.editoraai.smartedit**, separada das versões anteriores para preservar dados dos protótipos. Os dados do teleprompter e modelo de português são próprios de cada instalação. O APK é compilado em GitHub Actions (JDK17, Android SDK35, Media3 1.8.0, CameraX1.4.2).

**Teste recomendado:** importe uma gravação de ~20 segundos com fala + duas pausas de 1,5s. Gere legendas, detecte cortes, exporte com sem efeitos, compare com o original e só então teste Flash anime. Veja se a voz e música não ficam fora de sincronia.

**Privacidade:** transcrição e cortes são locais; não enviam o vídeo para um servidor. Publicar seu rosto é opcional e deve ser uma decisão confortável e segura (sem endereço, escola ou dados pessoais visíveis).
