# EditoraAI v0.8 — Teleprompter e câmera frontal integrada

## Gravar e editar no mesmo app

Na tela inicial, seção **01 Arquivos**, toque em **🎥 GRAVAR MEU ROSTO COM TELEPROMPTER**.

O novo gravador usa **CameraX 1.4.2** e fornece:

- **Prévia da câmera frontal** (ou traseira caso não haja câmera frontal).
- **Áudio pelo microfone** gravado junto com o vídeo.
- **Roteiro na tela** em uma caixa escura próxima da lente: cola/edita um texto, rola automaticamente durante a gravação e **não aparece no MP4**.
- Um roteiro de exemplo adaptável para a revelação de rosto do canal Poder Azul e os 300 seguidores, que pode ser substituído por qualquer texto.
- **Velocidade do texto** de 12 a 132 pixels lógicos por segundo e **tamanho de letra** de 18 a 44 sp.
- **Contagem de 3 segundos**, gravar, pausar, retomar, parar e salvar.
- **Salva em Filmes/EditoraAI na galeria** em Android 10+ com MediaStore. Em Android 8/9 grava no diretório privado de vídeos e solicita indexação à galeria.
- A gravação finalizada **volta automaticamente ao editor** como vídeo principal: pode criar legendas Vosk PT-BR offline, música, volume, memes, GIFs, SFX e exportação 9:16.
- Roteiro, velocidade e tamanho guardados no armazenamento privado do app.
- **Permissões explícitas de câmera e microfone** somente ao abrir o gravador. O app não começa a filmar antes da contagem e de tocar em gravar.
- O app evita deixar o teleprompter e o microfone ocultos durante uma gravação: exibe botão de parada, timer e status permanente.

## Como gravar a revelação

1. Abra **EditoraAI TELEPROMPTER** e toque **Gravar meu rosto com Teleprompter**.
2. Permita **câmera e microfone**.
3. Toque **Editar/colar roteiro** e use o texto completo da revelação. Configure tamanho e velocidade para conseguir ler perto da lente.
4. Toque **GRAVAR VÍDEO**. Aguarde a contagem 3–2–1. Leia sem correr.
5. Toque **PAUSAR** quando precisar e **RETOMAR** para continuar a mesma gravação.
6. Toque **PARAR E USAR VÍDEO**. O vídeo é salvo na galeria e aberto automaticamente no editor.
7. Gere legendas offline e importe somente trilhas musicais que você tem autorização para usar/publicar.
8. Em Mixagem Inteligente, escolha volume da voz e início da trilha no tempo desejado.
9. Exporte, verifique áudio, enquadramento e legendas antes de publicar.

## Compatibilidade e limitações

- Gravação com qualidade HD (720p), com fallback para qualidade disponível no celular. Saída orientada para vídeo vertical por meio da rotação e processamento do Android; o editor mantém formato exportado 720×1280.
- O roteiro é um **overlay de leitura**, não um texto queimado no vídeo capturado. O arquivo MP4 tem rosto e voz, sem roteiro.
- Prévia de câmera frontal pode ser espelhada; esta versão tenta alinhar o espelhamento do arquivo ao da prévia usando MirrorMode CameraX.
- Só uma câmera e uma gravação por vez. Se a câmera/microfone não estiver disponível ou a permissão for negada, o app mostra um aviso.
- O contador é iniciado na confirmação do gravador; podem ocorrer pequenas diferenças de tempo, dependendo do celular.
- Evite mostrar detalhes privados no cenário do vídeo (endereço, escola, documentos, localização).
- **Pacote debug separado:** `com.vini.editoraai.teleprompter`, instalado ao lado da v0.7 para não apagar projetos das versões anteriores. Não compartilha o modelo Vosk nem os dados privados com as versões antigas.

## Build

Android minSdk 26, targetSdk 35, CameraX 1.4.2, Media3 1.8.0, Java 17, Gradle 8.10.2. GitHub Actions gera `EditoraAI-v0.8.0-TELEPROMPTER-APK`.

A compilação garante que o app é compilável; não valida a captura real nem a sincronização no hardware do celular. Antes de gravar um vídeo longo, faça um teste de 10 segundos e confira voz e imagem na galeria.
