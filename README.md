# EditoraAI v0.5 • Analista de vídeo

Editor Android Java com legendas offline PT-BR, estilos e fontes, linha do tempo, músicas, imagens/memes e efeitos sonoros.

## O que a v0.5 realmente implementa

- Análise local da transcrição Vosk por palavras-chave e temas como **Sonic Rumble, Roblox, Free Fire, Minecraft e natureza**. É um mecanismo heurístico, não um grande modelo de IA online. O resultado são **sugestões de momentos** no vídeo.
- Tela independente **✦ Abrir Analista de Mídia**; você revisa e aprova cada mídia, evitando inserções indesejadas.
- Buscador de imagens do **Wikimedia Commons** por tema, mostrando título e licença. Exige internet para pesquisar e baixar. Nem todo assunto (especialmente jogos comerciais) tem mídia disponível sob licença aberta. O usuário deve cumprir as condições de atribuição/autoria indicadas pelo Commons.
- Importar imagens, screenshots, memes PNG/JPG/GIF e sons do armazenamento do Android. GIFs aparecem como quadros estáticos no vídeo.
- Inserção de imagens em intervalos aproximados de 3,6 segundos no ponto da fala ou no ponto atual. Imagens aprovadas entram na prévia e **são renderizadas na exportação MP4 9:16**.
- Efeitos sonoros **originais sintetizados** (pop, whoosh, vitória, boing e erro suave), mais áudio importado, com início temporizado. A exportação mistura a voz do vídeo com trilha musical e uma sequência de SFX.
- Gestão de mídias temporizadas por vídeo: exibe cenas adicionadas e permite remover. Mídias permanecem salvas ao reabrir o mesmo projeto.
- Compatível com as legendas offline que já funcionaram na v0.2.2 (Vosk modelo português v1/v2).
- Ainda mantém os estilos, fontes e 12 animações da v0.4.1.

## Passo a passo

1. Instale **EditoraAI ANALISTA** (`com.vini.editoraai.analista`), ao lado das versões antigas para proteger projetos.
2. Importe seu vídeo falando e gere as legendas offline. Este app separado terá de baixar o modelo PT-BR uma vez.
3. Toque em **✦ ABRIR ANALISTA DE MÍDIA** e escolha **Analisar transcrição**.
4. Para uma sugestão, escolha **Buscar imagem deste tema** ou **Usar imagem minha neste ponto**. Aprove somente conteúdo para o qual você tem permissão de uso e respeite créditos.
5. Use **Inserir efeito sonoro original** ou importe um áudio seu. Tudo é salvo na lista de mídias.
6. Volte ao editor e exporte 9:16.

## Limitações honestas

O analista sugere termos e tempos; **não faz reconhecimento semântico profundo** nem acessa pesquisa de imagens do Google, tampouco baixa cenas de jogos ou vídeos de terceiros de modo automático. Para jogos comerciais, use screenshots ou gravações suas ou material autorizado. A v0.5 faz **sobreposições estáticas** de imagem (não mistura vários clipes de vídeo), e GIF aparece como imagem parada. Efeitos visuais incluem transição de entrada tipo Pop/Fade nas imagens e as 12 animações já existentes nas legendas. Sons podem falhar se o arquivo de áudio não for suportado pelo Media3 e SFX sobrepostos podem ser limitados. Testar exportação com voz+música+SFX é recomendado em um aparelho real.

## Build e atualização

JDK 17 / Gradle 8.10.2 / Media3 1.8.0. GitHub Actions cria `EditoraAI-v0.5.0-ANALISTA-APK`. O pacote debug **não substitui** versões antigas: é uma instalação separada para proteger seus projetos. Não compartilha automaticamente mídia interna nem o modelo Vosk com instalações anteriores. Nenhuma chave de API é obrigatória para o recurso analista.
