# EditoraAI 🎬 — Editor de vídeo Android para Kwai

Versão de teste com ícone próprio (v0.1.1): projeto Android nativo em **Java + Media3**, sem anúncios, voltado para Shorts, Reels e Kwai.

## Novidade v0.1.1

- Ícone do app personalizado: arara azul, claquete e nome EditoraAI.
- A versão de teste mantém `applicationId` original, mas APKs debug de execuções diferentes do GitHub Actions podem ser assinados com chaves distintas; se o Android recusar a atualização, não desinstale sem salvar os projetos e arquivos importantes.

## O que está implementado

- Importar vídeo do celular, reproduzir prévia e recuperar o último projeto.
- Definir início e fim do corte usando duas barras.
- Converter e exportar MP4 **vertical 9:16 em 720×1280**, com enquadramento centralizado.
- Adicionar música extra, em loop, misturada ao áudio original (sem controle de volume ainda).
- Legendas automáticas **de verdade** usando Groq/Whisper: o app extrai a faixa de áudio AAC de um MP4 e envia **apenas o áudio** para transcrição em português, recebendo tempos por segmento.
- Revisar, editar, apagar e adicionar trechos de legendas, inclusive ajustar início e fim de cada trecho.
- Legendas gravadas diretamente nos quadros exportados (texto branco com contorno preto).
- Arquivo final salvo na galeria em `Filmes/EditoraAI` e botão para compartilhar.
- Gerar APK pelo GitHub Actions, sem precisar usar o notebook.

## Como baixar o APK

1. Abra o repositório no GitHub e toque em **Actions**.
2. Abra a execução **Gerar APK Android** (iniciada após cada push ou pelo botão *Run workflow*).
3. Após concluir com ✔, em **Artifacts**, baixe **EditoraAI-v0.1.1-APK**.
4. Extraia o `.zip` e instale o `app-debug.apk` no Android.

O APK de depuração não é uma versão de loja. Android 8.0 ou posterior, com desempenho dependente do aparelho.

## Como usar legendas IA

1. **A Groq exige usuários de 18 anos ou mais.** Se você for menor, não crie uma conta para contornar a regra. Um responsável pode verificar os termos e, se permitido, administrar a própria chave em https://console.groq.com/keys; nunca publique chaves no GitHub. Os recursos de edição manual funcionam sem a API.
2. Importe um vídeo gravado com áudio **AAC**.
3. Toque em **Transcrever fala automaticamente**, cole a chave e aguarde.
4. Revise as palavras e tempos; toque em **Exportar**.

**Privacidade:** o áudio é enviado à API Groq para transcrição. A chave Groq permanece somente na memória do app durante a sessão, não é gravada no repositório nem nas preferências. Os arquivos exportados e as legendas do projeto ficam salvos no celular. Não inclua nenhuma chave no código-fonte.

**Limitações da v0.1:** a transcrição necessita internet/chave Groq; o arquivo de áudio enviado deve ter até 25 MB; só está implementada a extração sem recodificação de áudios AAC encapsulados em MP4; ainda não há edição multicamadas, efeitos avançados, geração de vídeo por IA ou transcrição offline. A prévia das legendas é aproximada; a renderização final pode variar conforme o dispositivo. Teste com um vídeo curto primeiro.

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
- Groq API/Whisper Large V3 Turbo: legendagem por segmentos.
- MediaStore: salvar MP4 na galeria.

## Próximas versões planejadas

Trilha de edição com múltiplos clipes, mixagem com volumes, filtros, stickers e transições, biblioteca de fontes, cortes automáticos e transcrição local quando houver suporte de hardware/modelos. Recursos futuros **não estão implementados** nesta versão.
