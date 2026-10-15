# EditoraAI v0.6 NUVEM — YouTube de referência, Google Fotos/Drive e GIFs animados

## Funcionalidades implementadas

A base é a v0.5: importação de vídeo, corte, legendas Vosk **offline** em português brasileiro, fontes, animações, efeitos sonoros originais, pesquisa Wikimedia Commons, inserção de imagens/memes em intervalos e exportação 9:16 720x1280.

**Novidades da v0.6:**

- **YouTube**: buscar vídeos do tema no YouTube (abre a pesquisa no aplicativo/navegador), colar o link e salvá-lo com o projeto como referência para um trecho. Assista com o player incorporado (quando permitido) ou abra o app YouTube. O link **não é baixado** e o vídeo do YouTube **não entra na exportação do MP4**.
- **Google Imagens**: abre a busca do Google para você localizar material e conferir autoria/licença. Permite inserir um **link HTTPS direto de arquivo PNG, JPG, WebP ou GIF** (um arquivo por vez, limite de 8 MB). O app armazena apenas o item aprovado em seu cache interno para poder renderizar. Não é uma API de pesquisa do Google, e não faz scraping.
- **Google Drive/Google Fotos**: seletores nativos do Android para imagens e vídeos na nuvem, quando disponíveis no dispositivo. Em geral, Drive aparece em "Documentos" e Google Fotos pode aparecer no seletor de imagens. Nada obriga baixar packs. Acesso exige conexão em casos de conteúdo remoto. **Um vídeo escolhido na nuvem pode substituir o vídeo principal após confirmação**, sem upload prévio ao app.
- **GIFs de verdade**: preserva a animação dos GIFs de até 12 MB importados do celular, nuvem, Wikimedia ou URL. A prévia reproduz a animação e a exportação desenha os quadros conforme a posição de tempo. O formato GIF e seus tempos dependem de compatibilidade do decodificador Android.
- **Preservação**: pacote de teste separado `com.vini.editoraai.nuvem`, para não apagar dados/projetos do v0.5. O modelo do Vosk requer download novamente na nova instalação. O identificador de release permanece `com.vini.editoraai`.

## Teste rápido

1. Instale o app **EditoraAI NUVEM**. Na primeira vez, importe seu vídeo e gere legendas offline.
2. Abra **✦ ANALISTA DE MÍDIA** e clique em "Pesquisar vídeos no YouTube"; se gostar de um, copie o link e cole em "Guardar link de um vídeo do YouTube" para assistir como referência.
3. Escolha um GIF em "Importar meu meme" ou pelo seletor Google Fotos/Drive. Insira e volte ao editor: a prévia deve animar o GIF.
4. Exporte um MP4 9:16 e confira os GIFs ao longo do vídeo.
5. Como alternativa, importe um vídeo **que você pode usar** pelo seletor Google Fotos/Drive como principal ou insira uma imagem pela URL HTTPS.
6. Confira os créditos/licenças de qualquer mídia de terceiros. Mídias da nuvem podem exigir conexão; o provedor do Android pode transferir somente o arquivo selecionado, conforme necessário.

## Limitações e política do YouTube

A política oficial da API YouTube proíbe baixar, importar, armazenar em cache ou manter cópias de conteúdo audiovisual do YouTube sem aprovação prévia por escrito da plataforma: https://developers.google.com/youtube/terms/developer-policies?hl=pt-BR

Por isso a v0.6 **não realiza ripping/downloading de YouTube**. Você pode visualizar os vídeos por player incorporado / app oficial e usar arquivos próprios ou licenciados do Drive, Google Fotos ou armazenamento para a edição e exportação. A importação por URL obtém somente o arquivo de imagem aprovado, porque não existe renderização MP4 sem processar os bytes da mídia. Isso evita download manual de packs de dezenas de GB, mas **não elimina o tráfego de rede do arquivo selecionado**.

**GIFs**: serão reproduzidos sem áudio, e o Google Fotos/Drive pode fornecer permissões transitórias; para projetos a longo prazo prefira selecionar por Documentos (acesso persistente) quando disponível. O visual pode variar conforme hardware e conteúdo.

## Build

Compilar com JDK 17, Android SDK 35, Gradle 8.10.2 e Media3 1.8.0. GitHub Actions gera `EditoraAI-v0.6.0-NUVEM-APK`. Build bem-sucedida **não equivale a teste num celular real**; verifique exportação e reprodução de GIF com um vídeo de 10–20 segundos antes de trabalhar com vídeos longos.
