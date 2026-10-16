# EditoraAI v0.9 — Marca d'água Poder Azul

Esta atualização mantém os recursos anteriores: gravação com câmera frontal e teleprompter, mixagem de voz e música, Vosk PT-BR offline, sobreposições, GIFs, legendas, legendas animadas, efeitos e exportação MP4 vertical.

## Marca d'água personalizada

- Na tela principal: seção **MARCA D'ÁGUA • PODER AZUL**.
- Escolher imagem na galeria/documentos: PNG com fundo transparente é recomendado, mas JPG e WebP estáticos também funcionam.
- A imagem escolhida é copiada e convertida para PNG **no armazenamento privado do app**; não precisa selecioná-la a cada gravação.
- Mostra a marca d'água na **prévia do vídeo** e aplica o mesmo tamanho/posição ao MP4 exportado.
- Quatro posições: superior esquerda, superior direita, inferior esquerda e inferior direita.
- Tamanho de **12% a 45% da largura**, respeitando altura máxima de 32% do vídeo para não cobrir tudo.
- Transparência de **15% a 100%**.
- Opção ligar/desligar e remover o logo salvo; se não houver logo selecionado, nenhum watermark é exportado.
- O recurso usa Media3 BitmapOverlay para compor a imagem em todas as frames do vídeo, sem recodificação extra separada.
- Os ajustes do projeto são persistidos nas preferências. Alterar o vídeo não apaga o logo.

## Dica para o Kwai

Use PNG com fundo transparente, canto inferior direito, tamanho 18%–24% e opacidade 70%–85%. Evite esconder o rosto ou a legenda. Teste numa gravação de 10 segundos; a prévia mostra a posição aproximada, mas o MP4 é o resultado definitivo.

## Limitações

- Aceita imagens estáticas PNG/JPG/WebP; GIFs em movimento não são marcas d'água nesta versão.
- Logos grandes podem bloquear legendas. A imagem é redimensionada para no máximo 1500px durante a importação e limitada a 8MB no arquivo original.
- Em vídeos que já tenham logo (por exemplo um MP4 exportado do InShot com logo), adicionar outra marca resulta em **duas marcas d'água**. Importe a gravação original sem logo se quiser uma única marca.
- O conteúdo e a imagem não são enviados para um servidor para aplicação da marca d'água.
- APK debug separado **com.vini.editoraai.marca** para não desinstalar versões antigas; ao instalar precisa redownload do modelo Vosk para uso offline.

Compilação Android JDK17/SDK35 Gradle 8.10.2, CameraX 1.4.2, Media3 1.8.0. Compilar não substitui o teste em aparelho real.
