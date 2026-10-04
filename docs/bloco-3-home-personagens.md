# Bloco 3 — Home e catálogo de personagens

## Escopo entregue

Home com seis HQs recentemente publicadas, Spider-Man fixo como destaque e dois trechos do resumo factual da ComicVine: apresentação e “Você sabia?”. Catálogo com retratos reais em carrossel, busca pelo nome do registro, filtros de origem, time e gênero e carregamento progressivo. “Perfil” conserva o rótulo do Figma e abre as opções de gênero, conforme confirmação do usuário. “Saiba mais” abre uma página HTTPS verificada da ComicVine no navegador; detalhes internos são o próximo subbloco.

Java, XML, MVVM e Fragments mantidos. Adicionada RecyclerView 1.4.0 para as listas e centralização dos retratos, sem mudança de SDK, Gradle ou linguagem. Não há conteúdo simulado, autenticação de demonstração ou testes unitários neste bloco.

## Design

Referências em cache: Tela Inicial `19:102` e Personagens `38:428`, do arquivo Marvel - Mobile. Nenhuma chamada adicional ao Figma foi feita para os ajustes. Marca SUA centralizada acima de MARVEL, fontes Bebas Neue/Inter, painel escuro até o fundo e menu sobreposto reutilizados.

Home mantém hierarquia de últimas atualizações, destaque e curiosidade; a apresentação em duas colunas vira vertical em telas menores que 360 dp ou fonte acima de 130%. O cabeçalho de personagens reutiliza Inter 20 sp e oferece voltar ao início. Os retratos do catálogo preservam proporção aproximada 240 × 366 e destaque central maior. Busca, alvos de 48 dp, controles anterior/próximo e carregamento são adaptações funcionais. Marv aparece na curiosidade e nos estados reais de carregamento, vazio e erro.

## Contrato verificado com respostas reais

Inspeções autenticadas no GitHub Actions, sem publicar a chave:

- [Contrato de editora, personagens, volumes, datas e origens](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37207420753).
- [Times e relação de membros](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37207856051).

A editora exata **Marvel** foi identificada com ID 31 nesses relatórios. O aplicativo resolve novamente nome e detalhe e não incorpora esse ID como constante. Os filtros `publisher:31` dos endpoints `characters` e `volumes` foram **ignorados** pela API; a ordenação por `count_of_issue_appearances` também não se confirmou. Não são usados como garantia ou popularidade.

| Informação exibida | Endpoint e campos | Critério / ausência |
| --- | --- | --- |
| Identidade Marvel | `publishers`, filtro `name:Marvel`, `id,name,api_detail_url`; detalhe `publisher` | Nome exato único, URL válida e detalhe com mesmo ID/nome; falha bloqueia conteúdo oficial. |
| Índice de personagens | Detalhe da editora, `characters` | Referências associadas à Marvel, IDs/URLs válidos e deduplicados. |
| Retrato, nome, nome real, gênero, origem | `characters`, filtro de IDs em lote `id:A\|B`; `id,name,real_name,publisher,origin,gender,image,deck,site_detail_url` | ID solicitado e `publisher.id` da Marvel confirmados antes da exibição; campos opcionais vazios são ocultados. |
| Destaque | `characters`, filtro `name:Spider-Man` | Nome exato e editora verificada; seleção fixa autorizada, sem alegar popularidade. |
| Apresentação e curiosidade | `deck` do destaque | HTML convertido para texto seguro; tradução ML Kit de trechos, preservando o nome e nome real conhecidos fora do tradutor; primeiro período vira apresentação e segundo período vira curiosidade. São trechos da mesma fonte, sem invenção. Se não houver segundo trecho, ocultar curiosidade. |
| Opções de origem | `origins`, `id,name` | Catálogo auxiliar real; IDs preservados, rótulos traduzidos sob demanda. Não é catálogo exclusivo Marvel. |
| Filtro de origem | `character.origin.id` | Seleção local por ID, antes da exibição; o rótulo não altera a relação. |
| Filtro de gênero | `character.gender` | 1 masculino e 2 feminino verificados nas amostras; “Todos” inclui demais valores. Nenhuma opção adicional presumida. |
| Opções de time | Detalhe da editora, `teams` | Nomes próprios preservados; pesquisa na lista, sem chamadas por opção. |
| Membros de time | Detalhe de time, `id,publisher,characters` | Time precisa pertencer à mesma editora; IDs de membros intersectados com índice de personagens, além de conferir publisher em cada personagem. |
| HQs recentes | `issues`, `store_date`, `volume`, `issue_number`, `image`, `site_detail_url` | Data de publicação/venda `store_date`, até hoje, ordem decrescente; volume precisa constar na relação `publisher.volumes`. Não usa atualização do registro nem substitui por data de capa. |
| Imagens | `image.medium_url` | HTTPS da ComicVine, sem credencial, redirects ou URLs externas; ausência/falha conserva fundo neutro, sem retrato fictício. |
| Ver mais / Saiba mais | `site_detail_url` | HTTPS da ComicVine sem query/credencial; navegador externo. Não há WebView. |

### Seleção e limites

A busca usa o **nome do registro** do índice, sem distinguir maiúsculas ou acentos; não é busca por todos os aliases nem tradução oficial dos nomes. A ordem padrão coloca Spider-Man primeiro e depois usa nome/ID. Não representa ranking editorial.

Cada carregamento de personagens consulta até três lotes de 32 referências e para ao encontrar pelo menos 12 resultados. O cursor avança pelas referências examinadas; filtrados e carregados são deduplicados por ID. “Carregar mais” continua do cursor, inclusive quando uma seleção examinada ficou vazia. O contador informa apenas os personagens **carregados nesta seleção**, não um total de toda a Marvel.

A Home examina no máximo cinco páginas de 100 edições globais em `store_date:desc`, deduplica por ID e exibe até seis cujos volumes pertencem à Marvel. Esse limite contém chamadas à API; não promete percorrer todo o catálogo. Campos/ordem entre datas empatadas podem mudar no serviço. “Últimas atualizações” significa publicações, não registros modificados.

## Cache, idioma e chave

Respostas públicas em arquivos JSON privados com gravação atômica e metadados no SQLite, sem parâmetros secretos (os índices de vários MiB não passam pelo limite de linha do CursorWindow): 24 horas para identidade, índices, personagens, origens e times; 15 minutos para as consultas de publicações, cujo intervalo inclui a data atual. Na falha de rede, uma resposta já salva pode ser usada, passando novamente pelas mesmas verificações. Cache limitado a 300 respostas.

Imagens: memória limitada a 8 MiB e até 80 arquivos no cache privado, gravação atômica e redimensionamento antes de exibir. Traduções: cache persistente por entidade/campo/hash/idioma/versão existente; original preservado. Imagens e navegação não esperam tradução. Falha sem tradução salva mostra estado em português com retentativa. Nome e nome real conhecidos do destaque são preservados fora da tradução; nomes dos registros e títulos permanecem como registrados na fonte; a UI identifica “Tradução automática”. ML Kit usa `pt`, sem variante própria pt-BR.

A chave continua fora do APK: configure `.env` local na raiz e execute `python tools/comicvine.py device` com aparelho debug conectado. O Secret do GitHub somente provisiona o **emulador do CI**; baixar o APK não configura automaticamente um celular. Procedimento completo em [bloco-3-proposta.md](bloco-3-proposta.md). Nenhum valor de chave foi lido ou publicado nesta sessão.

O workflow Android volta a compilar/lint e não é substituído pelas inspeções. Atividades ADB de diagnóstico existem apenas em `src/debug`, sem launcher ou menu, e exercitam os repositórios reais; não são conteúdo do produto. O artefato de diagnóstico salva IDs públicos e resultados, sem URLs de requisição com chave.

## Roteiro manual

1. Instale o APK debug deste bloco, configure a chave via ADB e abra “Explorar sem entrar”. Espere capas, datas e Spider-Man; a tradução pode exigir o primeiro download do modelo por Wi-Fi.
2. Role a Home: confira resumo em português, nome real/origem, curiosidade com Marv e fonte. “Ver mais” deve abrir o registro correspondente na ComicVine.
3. Abra Personagens. Arraste retratos e use anterior/próximo; nome, metadados e link devem acompanhar o retrato central.
4. Busque `Spider-Man`. Selecione origem, gênero e um time; nomes/imagens devem respeitar a seleção. “Limpar filtros” preserva a busca; apague a busca para retornar à seleção geral.
5. Use “Carregar mais”. A seleção continua sem IDs duplicados. Uma falha de página mantém os itens já exibidos e oferece retentativa.
6. Volte à Home e retorne ao catálogo. Confira busca/filtros e seleção. Reinicie sem rede após carregar; dados e traduções já salvos devem ser recuperados.
7. Confira tela pequena, fonte 200%, rolagem e último controle acima do menu. Faça a validação em aparelho físico antes de aprovar o bloco.

## Verificação e checklist

Em andamento: compilação/lint e emulador. Os resultados efetivos serão registrados nesta seção após o CI. Sintaxe Java 11 (44 arquivos) e XML/Python conferidos localmente, sem resolução Android local. Não há SDK Android neste ambiente.

## Próximo subbloco proposto

Detalhes internos de personagem a partir do frame `42:478`, usando descrição traduzida, relações/poderes e primeira aparição somente quando verificados. Aguarda validação desta entrega; nenhuma tela de histórias, geração paga ou persistência de heróis foi acrescentada aqui.
