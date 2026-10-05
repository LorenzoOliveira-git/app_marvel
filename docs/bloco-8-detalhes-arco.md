# Bloco 8 — Detalhes nativos de arcos

O PR do bloco 7 foi integrado à `main` antes deste bloco. O catálogo agora abre uma tela nativa por **Ver arco**, mantendo busca, ordem e lista ao voltar. A tela apresenta imagem completa, título original, editora, resumo traduzido, contagem de vínculos e edições em linha do tempo. A descrição extensa é traduzida ao selecionar **Ler descrição**; não bloqueia imagem, resumo ou edições. As edições abrem o detalhe nativo existente. O link externo tem rótulo explícito na tela de detalhe.

## Contrato e informações

| Informação | Fonte e obtenção | Natureza e ausência |
|---|---|---|
| Identidade Marvel | Resolver `publishers/name:Marvel`, índice `publisher.story_arcs`, detalhe `story_arc` com ID/caminho e `publisher` conferidos | Direta; identidade inconsistente ou arco externo é rejeitado |
| Nome, imagem, fonte | `story_arc.name`, `image.medium_url`, `site_detail_url` | Direta; imagem/fonte vazias ocultam os controles |
| Resumo e descrição | `deck`, `description`; HTML convertido em texto simples; tradução ML Kit en→pt | Tradução automática identificada; original em cache; ausência oculta a seção, falha oferece retry independente |
| Quantidade | IDs únicos de `story_arc.issues` com caminho de issue validado | Derivada: “edições vinculadas na ComicVine”; não promete quantidade de edições Marvel já hidratadas |
| Edições | Referências diretas `story_arc.issues`; `/issues/filter id` em lotes de até 12; `/volumes/filter id` por lote | Caminhos/IDs e retorno completo conferidos; volume precisa pertencer ao índice Marvel e seu próprio publisher ser Marvel antes de exibir |
| Sequência | Posição das referências diretas em `story_arc.issues`; não utiliza a ordenação do endpoint de hidratação | Ordem fornecida pelo catálogo, explicitamente sem validação como ordem oficial de leitura; sem reordenar por número entre volumes |
| Data do card | `issue.cover_date` | Data de capa, identificada; ausente/inválida oculta; não confunde `store_date` nem atualização do registro |
| Título de edição | Nome do volume verificado e `issue_number`, quando presente | Derivado; preserva nomes originais; referências com `name=null` continuam válidas por ID/caminho |

O contrato real do bloco 7 registrou `"Avengers" Civil War` (40615) com 123 referências e `count_of_isssue_appearances=0` (grafia da API, com três s). Por isso a contagem exibida usa os vínculos reais, e não esse campo. Não se presume suporte a filtro de publisher em `/story_arcs`: a sondagem anterior mostrou que ele era ignorado. Hidratação sempre usa referências canônicas.

Não há fatos simulados. IDs de Civil War, 1602 e arco externo aparecem somente nos diagnósticos debug. Não são filtros do produto. Personagens/criadores agregados, adaptações, período e primeiras/últimas edições não entram neste bloco sem contrato confiável; nenhum episódio ou temporada é criado.

## Arquitetura e comportamento

- `CatalogModels.ArcDetails` conserva originais e vínculos imutáveis. `MarvelRepository.arcDetails/arcIssues` concentra cache, identidade e paginação. No máximo três lotes sem itens elegíveis são examinados por chamada; pode haver **Carregar mais** após uma página vazia.
- `ArcDetailsViewModel` vinculado ao Fragment separa estado de identidade, resumo, descrição e lista. Paginação evita chamadas simultâneas e duplica nenhum ID; falha de próxima página conserva conteúdo/cursor e permite retry. Versões descartam callbacks após recarga/destruição.
- `CatalogDescriptions.translateArc` protege nome, aliases, títulos vinculados e textos dos links HTML. Reutiliza cache persistente por entidade/campo/hash e versão. Nomes oficiais localizados não são inventados.
- Java, MVVM, Fragments, XML e ViewBinding; componentes, tokens, cards, Marv e estados existentes. SDKs e dependências mantidos. Não existe frame específico de detalhe de arco no mapeamento do Figma; a tela reutiliza o padrão nativo dos detalhes já aprovado.
- `ArcDetailsCheckActivity` exportada apenas em debug permite diagnóstico com fontes reais; não aparece na navegação release. Não foram adicionados testes unitários. APK não é publicado em execução automática.

## Verificação

A [execução integrada](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37246907978), sobre `61753c9`, passou compilação/lint, ML Kit/cache, catálogos reais de personagens e quadrinhos, catálogo de arcos e todo o novo detalhe de arco. A execução ficou com conclusão geral de falha porque o roteiro visual antigo de quadrinhos não encontrou `first_more` ao rolar o perfil. Os diagnósticos de dados de quadrinhos, online e offline, passaram; não houve falha nova de rede nesse ponto. O único log de credencial ausente era anterior, da verificação intencional do catálogo. O roteiro passou a localizar o título da primeira aparição antes do botão e usar movimentos lentos; dados/código de produção das edições permaneceram iguais.

No detalhe real de arco, relatórios online e offline têm `success=true`: Civil War com **123 vínculos**, duas páginas de **12 edições**, sem IDs duplicados, na sequência das referências, exclusão do arco externo The Killing Joke e rejeição de cursor negativo. A descrição traduzida tem 28.140 caracteres e conserva “Captain America”. Após matar o processo e desligar a rede, a descrição veio do cache e o conteúdo foi idêntico (desconsiderando apenas a flag de origem do cache). A segunda amostra, `"1602" 1602`, também teve identidade e disponibilidade dos campos registradas.

Foram revisadas **14 capturas** do novo fluxo: imagem/título, resumo, primeira edição, detalhe nativo da edição, retorno, primeiras edições preservadas após carregar mais, descrição, retorno ao catálogo, 320×640, 640×1000 e fonte 200%. Não houve texto truncado por limite de linhas ou controle inacessível nos percursos executados. A navegação inferior flutua sobre o conteúdo, seguindo o padrão já aprovado; a rolagem/insets permitem alcançar o conteúdo abaixo dela.

O lint integrado registrou **0 erros e 32 avisos**: os 30 anteriores, um rótulo externo que ficou sem uso e a recomendação de plural na contagem. Os dois novos avisos foram corrigidos nos recursos: rótulo removido e contagem como “Edições vinculadas na ComicVine: N”, também correta para N=1. A [execução final isolada](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37249114131), sobre `417c064`, recompilou com os recursos corrigidos e registrou **0 erros e 30 avisos**, sem novos avisos deste bloco.

A [primeira repetição isolada de quadrinhos](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37248636150), sobre `336e3ff`, parou na consulta inicial com `NETWORK/HTTP=0` imediatamente após o modo offline da preparação ML Kit. O roteiro passou a exigir rede validada em três observações consecutivas, limpar logs antes de cada diagnóstico e permitir até duas novas tentativas online exclusivamente quando todas as falhas novas forem `NETWORK/HTTP=0`. Não repete o roteiro completo, erro HTTP, dados inválidos ou diagnóstico sem conclusão.

A **execução final isolada passou**, incluindo compilação/lint, ML Kit/cache e todo o roteiro de quadrinhos. Os relatórios `issue.json` e `issue-cache.json` têm `success=true`; foram revisadas **15 capturas**: Home/catálogo → edição, perfil → primeira aparição → edição, descrição, personagens relacionados, navegação para perfil/retorno, créditos, aparições → edição e tamanhos/fonte. O botão de primeira aparição foi encontrado com o movimento lento e título verificado antes do toque. Catálogos e detalhe de arcos previamente aprovados foram preservados; somente rótulos de arco receberam ajustes posteriores, sem alteração dos dados/carregamento/navegação.

Sem APK publicado e sem testes unitários adicionados. Credencial fora do APK conferida no emulador. Permanecem pendentes aparelho físico, TalkBack e cenários manuais de falha/retry não induzidos pelo diagnóstico.

## Roteiro manual

1. No Android Studio, selecionar `codex/bloco-8-detalhes-arco` (ou `main` após incorporar o PR), sincronizar o projeto e executar **Sua Marvel (local)** com o `.env` existente.
2. Explorar sem entrar → Histórias → Explorar arcos de história → buscar **Civil War** → **Ver arco**. Conferir título, imagem, Marvel, resumo, fonte/tradução e quantidade identificada como vínculos.
3. Em **Edições do arco**, conferir o aviso de sequência e datas de capa; abrir **Ver quadrinho** e voltar. Carregar mais: primeiras edições permanecem e não duplicam.
4. No fim da lista carregada, **Ler descrição**. Conferir tradução, nomes próprios preservados e ausência de HTML. Voltar ao catálogo: busca, ordenação e resultados permanecem.
5. Após carregar online, matar o processo e repetir offline. Conteúdo já consultado e traduções em cache devem permanecer. Conteúdo nunca consultado pode apresentar erro/retry sem invalidar outras seções.
6. Conferir tela pequena/grande, fonte 200%, TalkBack, rotação, retorno entre abas e falha de rede no aparelho físico. Validação física e TalkBack permanecem pendentes.

## Checklist

| Item | Estado e evidência |
|---|---|
| Design e componentes | Atendido; 14 capturas do novo fluxo revisadas |
| Navegação e hierarquia | Atendido; arco → edição → retorno → catálogo → Home no emulador |
| Regressões | Atendido nas verificações executadas; catálogos e detalhes anteriores passaram |
| Tamanhos/fonte | Atendido; 320×640, 640×1000 e fonte 200% revisados |
| Java/MVVM/Fragments/XML | Atendido; sem mudanças estruturais |
| Decisões/pendências | Sequência e contagem explicitadas; sem ordem oficial presumida |
| Estados e recuperação | Implementados por seção; erros de página preservam a lista |
| Sem testes unitários | Atendido |
| Dados mapeados | Atendido na tabela; sem dados simulados de produção |
| Credenciais/proprietário | Segredo em arquivo privado debug; conferência fora do APK no CI; heróis personalizados não aplicáveis |
| Exclusividade Marvel | Índice e publisher do arco; índice de volumes + publisher do volume antes de exibir edição |
| Português/tradução/cache | Atendido; descrição idêntica offline e cache confirmado |
| Formulário/imagem de herói | Não aplicável |

## Próximo bloco proposto

Catálogo de filmes Marvel por Histórias: validar primeiro endpoints e relações confiáveis da ComicVine, reaproveitar o frame Filmes e Séries, implementar imagem/título/dados disponíveis, busca e paginação. Os detalhes de filme ficam para um bloco posterior. Se a API não oferecer associação Marvel confiável, registrar o bloqueio antes de implementar o catálogo. Aguarda validação do bloco 8.
