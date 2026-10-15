# EditoraAI 🎬 — Editor de vídeo Android para Kwai

Versão v0.2.0 com legendas offline e ícone próprio: projeto Android nativo em **Java + Media3**, sem anúncios, voltado para Shorts, Reels e Kwai.

## Novidade v0.2.0 — legendar sem API

- **Legendas automáticas em português brasileiro diretamente no aparelho**, usando o motor open source Vosk. Reconhece a faixa de áudio de vídeos importados (não precisa gravar o microfone e não exige conexão na hora da transcrição).
- **Primeira utilização:** solicitação explícita para baixar modelo pequeno PT-BR (~31 MB compactado e espaço extra para instalação) pela URL oficial do projeto Vosk. Recomenda-se Wi-Fi. O modelo fica salvo só no armazenamento privado do aplicativo e é reutilizado sem internet.
- **Sincronização:** decodificação de áudio Android MediaCodec → 16 kHz mono → Vosk, gerando legendas curtas a partir dos carimbos de tempo por palavra. Pode demorar em celulares de entrada; palavras, acentos e pontuação podem sair errados e devem ser revisados.
- **Privacidade:** o reconhecimento offline não envia o vídeo nem o áudio a serviços externos. Opção Groq/Whisper anterior continua separada, opcional e depende de autorização/termos do provedor.
- O projeto/identificador do pacote permanecem os mesmos; legendas anteriores ficam preservadas até que o usuário confirme uma nova transcrição com resultado.

## Novidade v0.1.1

- Ícone personalizado com arara azul e claquete.
- O APK de depuração pode ter assinatura diferente entre execuções; guarde o projeto antes de substituir/desinstalar versões.

## O que está implementado

- Importar vídeo do celular, reproduzir prévia e recuperar o último projeto.
- Definir início e fim do corte usando duas barras.
- Converter e exportar MP4 **vertical 9:16 em 720×1280**, com enquadramento centralizado.
- Adicionar música extra, em loop, misturada ao áudio original (sem controle de volume ainda).
- Modo opcional de legendas online usando Groq/Whisper: o app extrai a faixa de áudio AAC de um MP4 e envia **apenas o áudio** para transcrição em português, recebendo tempos por segmento.
- Revisar, editar, apagar e adicionar trechos de legendas, inclusive ajustar início e fim de cada trecho.
- Legendas gravadas diretamente nos quadros exportados (texto branco com contorno preto).
- Arquivo final salvo na galeria em `Filmes/EditoraAI` e botão para compartilhar.
- Gerar APK pelo GitHub Actions, sem precisar usar o notebook.

## Como baixar o APK

1. Abra o repositório no GitHub e toque em **Actions**.
2. Abra a execução **Gerar APK Android** (iniciada após cada push ou pelo botão *Run workflow*).
3. Após concluir com ✔, em **Artifacts**, baixe **EditoraAI-v0.2.0-APK**.
4. Extraia o `.zip` e instale o `app-debug.apk` no Android.

O APK de depuração não é uma versão de loja. Android 8.0 ou posterior, com desempenho dependente do aparelho.

## Como usar legendas sem internet

1. Abra o app e importe um vídeo que tenha áudio.
2. Toque em **✦ Gerar legendas SEM INTERNET**.
3. Se for a primeira vez, autorize o download do modelo Vosk PT-BR (aprox. 31 MB). Aguarde a instalação.
4. A IA transcreve no celular e insere legendas com tempos aproximados. Revise tudo em **Editar legendas**.
5. Exporte o vídeo vertical e compartilhe no Kwai. Nas próximas vezes, o passo de download não se repete.

## Modo opcional Groq/Whisper

1. A Groq exige usuários de 18 anos ou mais; menores não devem abrir conta. Um responsável pode ler os termos e, se adequado, administrar uma chave própria em https://console.groq.com/keys. Nunca publique chaves.
2. Importe um vídeo gravado com áudio **AAC**.
3. Toque em **Usar Groq/Whisper (opcional)**, cole a chave do responsável caso seja apropriado e aguarde.
4. Revise as palavras e tempos; toque em **Exportar**.

**Privacidade:** o áudio é enviado à API Groq para transcrição. A chave Groq permanece somente na memória do app durante a sessão, não é gravada no repositório nem nas preferências. Os arquivos exportados e as legendas do projeto ficam salvos no celular. Não inclua nenhuma chave no código-fonte.

**Limitações da v0.2:** apenas o modo Groq necessita internet/chave; no modo offline, a precisão depende da clareza do áudio e do modelo pequeno; o arquivo de áudio enviado deve ter até 25 MB; só está implementada a extração sem recodificação de áudios AAC encapsulados em MP4; ainda não há edição multicamadas, efeitos avançados, geração de vídeo por IA nem ajuste automático de pontuação no modo offline. A prévia das legendas é aproximada; a renderização final pode variar conforme o dispositivo. Teste com um vídeo curto primeiro.

## Build local

Requisitos: JDK 17, Android SDK 35, Gradle 8.10.2.

```sh
gradle :app:assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## Tecnologias

- Android Media3 ExoPlayer: prévia.
- Android Media3 Transformer, effect e OverlayEffect: recorte, redimensionamento, efeitos e exportação.
- Android MediaExtractor/MediaMuxer: extrair áudio sem enviar vídeo ao serviço.
- Vosk Android 0.3.47 + modelo PT-BR pequeno (Apache 2.0): reconhecimento local de fala.
- Groq API/Whisper Large V3 Turbo: legendagem opcional online por segmentos.
- MediaStore: salvar MP4 na galeria.

## Próximas versões planejadas

Trilha de edição com múltiplos clipes, mixagem com volumes, filtros, stickers e transições, biblioteca de fontes, cortes automáticos e melhorias na precisão, sincronização e estilos de legenda. Recursos futuros **não estão implementados** nesta versão.
