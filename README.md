# EditoraAI v0.3 ESTÚDIO — legendas e timeline

Atualização incremental baseada na **v0.2.2 REPARO**, mantendo a validação corrigida do modelo Vosk de português brasileiro (arquivos legados v1 ou v2). Ainda usa Java nativo, Media3 e Vosk, sem API obrigatória.

## Novidades v0.3
- Quatro estilos de legenda no menu: Clássico branco, Neon ciano, Destaque amarelo e Clean em cartão branco.
- Efeitos de entrada na legenda: Sem animação, Pop (escala) ou Fade (opacidade), aplicados no vídeo exportado.
- Prévia de legendas com as mesmas cores e efeitos de entrada (aproximada à exportação).
- Linha do tempo principal com SeekBar, relógio e avanço manual (seek).
- Faixa de trechos de legenda tocáveis; um toque leva a reprodução ao instante correspondente.
- Preferências de estilo e animação salvas no projeto.
- Exportação MP4 9:16 (720×1280) mantém corte, áudio, música e legendas.

## Nota sobre os efeitos
Os efeitos **animam a entrada de cada frase** nos primeiros ~190 ms. Não há efeitos de partículas, máscara de vídeo nem marcação palavra por palavra nesta versão. A exportação usa quadros com transparência calculados pelo tempo; a prévia é uma representação usando TextView, podendo apresentar pequenas diferenças em relação ao MP4 final.

## Teste Android
Para evitar desinstalar a v0.2.2 REPARO, a build debug chama-se **EditoraAI ESTÚDIO** e tem package id `com.vini.editoraai.estudio`. Ela fica lado a lado com os outros APKs de teste e usa armazenamento próprio (o download Vosk pode precisar ser repetido). A build release conserva `com.vini.editoraai`, mas não há promessa de atualização direta de builds assinados por chaves de debug distintas.

1. Importe um vídeo com fala de 10–20 segundos.
2. Gere legendas offline e confira a contagem.
3. Selecione Neon ciano e animação Pop.
4. Toque em algum dos trechos na faixa de legendas e veja a prévia.
5. Exporte 9:16 e confira as legendas no arquivo MP4 salvo.

## Compilação
O workflow GitHub Actions executa `gradle :app:assembleDebug` com JDK 17 / Gradle 8.10.2 / Android SDK. O APK é publicado como `EditoraAI-v0.3.0-ESTUDIO-APK`.

## Segurança e privacidade
No modo offline a voz fica no aparelho; o modelo PT-BR é baixado mediante consentimento. A integração online Groq continua opcional e sujeita aos termos do serviço. O app não possui anúncios.
