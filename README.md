# EditoraAI v0.7 MIXAGEM — mixagem inteligente para narração e música

A versão 0.7 mantém a v0.6 (legendas Vosk offline, fontes, GIFs, suporte a Google Fotos/Drive, referências YouTube, analista, sobreposições e exportação 9:16) e adiciona controle do áudio exportado.

## Novo controle no painel Arquivos — Mixagem Inteligente

- **Voz do vídeo**: controla a amplitude do áudio original de 0 a 180% (100% preserva o original). Aumentar muito pode distorcer/clipping; comece em 100%.
- **Música de fundo**: controla o volume da faixa de 0 a 100%, com valor inicial de 26%. É mixada junto com o áudio do vídeo, não substitui a narração.
- **Início da música**: controle de precisão 0,1 segundo, contado desde o começo do **vídeo exportado**, depois do corte. Por exemplo 00:11.0 para começar na revelação do rosto. Antes desse ponto, a faixa musical fica silenciosa.
- **Redução automática durante a fala**: o processamento consulta os intervalos das legendas geradas pelo Vosk e usa o volume reduzido configurado (padrão 28% do volume original da música) durante a fala. A transição é suavizada para evitar cliques de áudio. **Esta versão não faz detecção de voz independente**; se não houver legendas, a música ficará com volume constante.
- **Fade de entrada curto** da música (260ms).
- A trilha pode repetir até o final do vídeo sem repetir o silêncio inicial.
- Os valores ficam salvos localmente no projeto; nenhum serviço de IA online ou chave de API é necessário.

## Como criar o vídeo de revelação

1. Grave o vídeo em que você aparece, importe no EditoraAI.
2. Importe uma música que tenha permissão para utilizar/publicar.
3. Ajuste voz para 100%, música para 20–30%, início para **00:11.0**, habilite redução automática e escolha música durante a fala entre 20 e 40%.
4. Gere legendas offline, revise e exporte.
5. Assista na galeria para conferir os níveis. **A prévia do editor ainda reproduz só o áudio bruto do vídeo**; os controles de mixagem são aplicados ao arquivo exportado.

## Direitos autorais

Possuir um arquivo de música de Mario Odyssey não significa ter autorização para publicá-lo; o Kwai pode silenciar ou restringir um vídeo com trilha protegida. Use a biblioteca licenciada da plataforma quando disponível, ou uma faixa própria/licenciada.

## Instalação e testes

APK debug usa `com.vini.editoraai.mixagem`, instalado ao lado dos apps anteriores para preservar projetos. O Vosk exige download inicial novamente, pois cada pacote tem pasta privada diferente. A versão release mantém o identificador original.

Compilação: JDK 17, Android SDK 35, Media3 1.8.0, Gradle 8.10.2. Artefato `EditoraAI-v0.7.0-MIXAGEM-APK`.

A compilação garante código-fonte Java válido, mas não testa mixers, codecs ou fontes num Android real. Confirme a mixagem exportando primeiro um vídeo de 10–20 segundos com voz e uma música licenciada.
