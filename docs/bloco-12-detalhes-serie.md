# Bloco 12 — Detalhes de séries e episódios

Histórias → Séries → Detalhes da série, no destaque e na seleção. Java/MVVM/XML, recursos visuais compartilhados com detalhes de filmes. Imagem, título, ano, editora, quantidade cadastrada, personagens Marvel com perfis nativos, descrição traduzida sob demanda e episódios em lotes de 12. Ausências ocultam seções; falhas permitem repetir sem perder os itens já carregados.

## Contrato primário

[Execução 37290599976](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37290599976), artefato 11335634703. `series/4075-ID/`: valida ID, caminho, editora Marvel 31 e caminho publisher/4010-31. Campos diretos: image, name, start_year, count_of_episodes, deck, description, characters, episodes, first_episode, last_episode. Nomes próprios e títulos originais preservados. HTML é convertido em texto seguro antes de tradução pelo serviço existente com cache persistente; original é conservado no modelo/resposta.

`episodes` fornece referências com ID/caminho/nome/episode_number. Ordenação derivada da numeração integral (numérica quando possível, desempate por ID), sem inferir temporadas. `episodes/` aceita filtro por IDs múltiplos e fornece air_date e series. Uma consulta por lote de 12, sem detalhe por item: valida todos os IDs/caminhos e vínculo recíproco com a série, antes de exibir. Datas ausentes ou inválidas são ocultas; datas válidas têm rótulo de exibição informada. A lista é parcial enquanto houver referências a carregar.

`first_episode`/`last_episode` são indicações da fonte, exigem presença na lista validada. Não significam sequência cronológica calculada nem fim definitivo. A sondagem mostrou inconsistências: Agatha possui 9 referências e último indicado 108. Não se corrige essa informação por suposição. Filtro de séries em episódios foi observado, mas a implementação usa o índice de referências e lote de IDs para não admitir episódios estranhos.

Personagens são cruzados com índice canônico Marvel antes de carregamento progressivo. Sem relações de quadrinhos, adaptações ou episódios recentes presumidos; não há campo de temporada confirmado. Títulos licenciados continuam identificados como tais pelo critério de editora da fonte.

## Mapeamento exibido

| Informação | Campo e obtenção | Natureza / ausência |
|---|---|---|
| Título, imagem e ano | series: name, image.medium_url, start_year | Direta; ano/imagem ausentes ocultos |
| Editora | series.publisher, ID e caminho canônico Marvel | Direta, requisito de entrada |
| Quantidade cadastrada | series.count_of_episodes | Direta, não total de temporadas; ausente oculto |
| Descrição/resumo | series.description/deck → HTML seguro → tradução com cache | Tradução automática sob demanda; falha mostra recuperação em português |
| Personagens | series.characters → índice Marvel → lote de characters | Relação direta e filtrada; vazia oculta |
| Episódios | series.episodes → lote por IDs em episodes | Relação recíproca validada; lotes de 12 |
| Nome e código do episódio | episodes.name/episode_number | Direta, código integral preservado |
| Data de exibição | episodes.air_date | Direta, formato brasileiro; inválida/ausente oculta |
| Primeiro/último indicado | series.first_episode/last_episode ∩ episodes | Declaração da fonte, sem cronologia inferida |
| Ordem da lista | episode_number numérico ou textual, desempate por ID | Derivada e rotulada; sem temporadas inferidas |
| Links externos | site_detail_url com tipo e ID correspondentes | Direta, URL inválida/ausente oculta |

Sem mocks, dados simulados, relações calculadas com quadrinhos ou custos novos.

## Verificação

[Execução completa 37292323533](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37292323533), commit `335a384c7e861acb99ecf6808e2c7696567e9398`: build/lint, ML Kit/cache, regressão do catálogo de séries e detalhes nativos aprovados. Verificou seleção de Agatha e destaque Agents, perfil de Quake e retorno, 12 → 24 episódios, descrição traduzida, retorno ao catálogo e layouts 320×640, 430×932, 640×1000 e fonte 200%. Foram revisadas 24 capturas dos detalhes e os relatórios reais.

Dados online/offline idênticos, exceto o indicador de origem do cache de tradução. Agents teve 136 referências, primeiro ID 1/Pilot/código 101/data de exibição 24/09/2013, último indicado ID 32766; os dois lotes trouxeram 24 IDs únicos. Agatha teve 9 referências e descrição em português com Agatha Harkness preservada. Série DC 331 e ID inválido foram rejeitados.

A conferência final [37295571208](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37295571208), commit `a7300bddfbed7600986074e0ee373c34d1b39014`, passou após ajustar o plural do contador e o enquadramento das capturas. Build/lint, ML Kit e dados/cache foram novamente aprovados. Foram revisadas 16 capturas finais: título, metadados, primeiro/último indicados e cards de episódios, nas telas pequena, normal, ampla e fonte 200%. Os cards Pilot e 0–8–4 mostram número, data e consulta externa legíveis. Lint final: zero erros e 36 avisos já existentes antes deste bloco, sem novos avisos. Evidências finais: android-preview 11338837407, android-catalog-check 11338688085 e android-lint 11338373848. O repositório/API, tradução e ações de navegação permanecem iguais à execução completa. O roteiro final repetiu dados/cache e layouts; a execução completa anterior fornece a evidência de catálogo, perfis, paginação e descrição. Os dois relatórios finais online/offline novamente coincidiram, com tradução recebida do cache offline. Sem testes unitários adicionados ou executados. Aparelho físico não verificado. Roteiro: abrir destaque e seleção; conferir ano/quantidade; abrir personagem e voltar; carregar mais episódios; ler descrição; retornar ao catálogo; repetir offline após cache; conferir tela pequena/grande e fonte 200%.

## Roteiro manual

1. Entrar como visitante, abrir Histórias → Séries e tocar Detalhes da série no destaque. Esperado: Agents of S.H.I.E.L.D., ano 2013 e 136 episódios cadastrados.
2. Abrir Quake na seção de personagens e voltar. Esperado: perfil nativo e retorno aos detalhes.
3. Conferir primeiro/último indicados e o card Pilot. Esperado: código 101 e exibição informada em 24/09/2013, sem temporada deduzida.
4. Carregar mais episódios. Esperado: contador de 12 para 24 referências e IDs distintos.
5. Ler a descrição e abrir Agatha pela seleção do catálogo. Esperado: texto em português identificado como tradução automática e nomes próprios preservados.
6. Após carregar online, repetir sem rede e conferir rolagem com fonte ampliada. Esperado: respostas/traduções em cache; links externos de episódios continuam identificados como ComicVine.

## Critérios de entrega

| Critério | Estado e evidência |
|---|---|
| Design e componentes | Atendido na implementação: padrão dos detalhes de filmes, sem frame específico de detalhes de série |
| Navegação e hierarquia | Atendido: abertura por destaque/seleção, perfil de Quake e retorno no emulador |
| Regressões afetadas | Atendido: regressão do catálogo aprovada; outros fluxos não foram reexecutados neste bloco |
| Tela pequena/grande e fonte ampliada | Atendido: 16 capturas finais revisadas, título/metadados/episódios legíveis nas quatro configurações |
| Java/MVVM/Fragments/XML | Atendido, sem Compose ou alterações de SDK/dependências |
| Decisões e limites | Atendido: numeração integral e primeiro/último como indicação da fonte |
| Estados e recuperação | Atendido por implementação e fluxo: Marv, repetição, paginação de 12 → 24 e retenção de itens |
| Testes unitários | Atendido: nenhum adicionado ou executado |
| Mapeamento de dados | Atendido: tabela acima e sondagem real sanitizada |
| Credenciais/conta | Credencial debug fora do APK; conta/heróis não se aplicam neste bloco |
| Conteúdo Marvel | Editora da série e relações canônicas verificadas antes da exibição |
| Português/tradução/cache | Atendido: tradução real e cache após reiniciar o processo/offline |
| Campos de herói/imagem fixa | Não aplicável neste bloco |

## Próximo bloco proposto

Formulário guiado de criação de herói com Marv, seleção real de origens e poderes e revisão dos dados, antes da integração de geração paga.
