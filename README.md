# EditoraAI v0.4 • ESTÚDIO DE TIPOGRAFIA

Mais opções de fontes e animações para legendas verticais de vídeos curtos, mantendo o reconhecimento offline Vosk corrigido, corte, música, prévia, edição de texto e exportação 9:16 MP4.

### Fontes (12 opções)

Impacto Black, Moderna Sans, Condensada Bold, Rounded, Serif, Mono, Casual, Cursive, Light, Medium, Serif Bold e Small Caps. Utiliza fontes já presentes no Android, sem downloads externos. A aparência exata pode variar conforme o fabricante do aparelho, caso uma família não esteja disponível.

### Animações (12 opções)

Sem animação, Pop elástico, Fade, Subir, Esquerda, Direita, Bounce, Zoom, Girar, Digitação, Pulse e Cair de cima. Efeitos animados na prévia e na exportação; a transição da prévia é uma aproximação do resultado renderizado no MP4. **Não são efeitos oficiais nem recursos do CapCut**.

### Aparência

Oito visuais: Clássico, Neon, Destaque amarelo, Clean, Glow rosa, Cinema, Black pill e Sunset. Tamanho entre 70 e 145% e posição inferior, central ou superior. Configurações salvas no projeto local do próprio app.

### Instalação e privacidade

A v0.4 é **EditoraAI TIPOGRAFIA**, pacote de teste `com.vini.editoraai.tipografia`. Instala-se lado a lado com a v0.3 ESTÚDIO e a v0.2.2 REPARO, pois os APKs GitHub Actions debug podem ter assinaturas diferentes. Isso protege seus projetos antigos, mas exige importar o vídeo e baixar o modelo de idioma novamente nesta versão. A build release mantém o applicationId original `com.vini.editoraai`.

O app trabalha offline após obter o modelo Vosk no primeiro uso. A Groq é opcional e sujeita aos termos do fornecedor; não coloque API keys no GitHub.

### Teste manual

Importe vídeo de 10s, gere legendas, escolha fonte Impacto ou Cursive, animação Zoom/Digitação e cor Sunset, ajuste o tamanho, visualize as legendas, exporte MP4 e confira se a animação ficou no vídeo. Compile confirmado pelo GitHub Actions, mas só um teste real no celular confirma renderização e performance.

### Limitações

Ainda não possui importação de arquivos de fontes externas nem animação palavra-por-palavra ou tracking automático de sílabas; o Digitação anima cada trecho de legenda progressivamente. Não há linha do tempo multiclipes nem efeitos oficiais da marca CapCut.
