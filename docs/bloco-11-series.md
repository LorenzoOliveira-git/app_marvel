# Bloco 11 — Catálogo de séries Marvel

Rota: Histórias → Séries Marvel. Catálogo nativo Java/MVVM com destaque Agents of S.H.I.E.L.D., carrossel, seleção, busca pelo título original, ordem A–Z/Z–A e carregamento progressivo. Consulta externa à ComicVine identificada nos botões; detalhes nativos ficam para o próximo bloco.

## Contrato real e limites

Sondagem primária [37282113529](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37282113529): `/series_list/` e `/series/4075-ID/` disponíveis. `publisher:31` é ignorado e devolve títulos DC e de outras editoras. O app confere em cada registro ID, nome Marvel e caminho canônico da editora, além do tipo/ID da série e do link público. O campo `series` não veio no detalhe da editora, portanto não há índice presumido. Busca `name` e ordenação `name` foram observadas em respostas reais.

Lotes de 100, no máximo três por ação, com cursor absoluto e deduplicação. Nenhuma chamada de detalhe por item. Resultado parcial indica isso na interface; incluem-se títulos licenciados com editora Marvel na fonte, sem afirmar que tudo pertence ao MCU. As relações ficcionais de arco/volume/saga e as datas do frame foram removidas. Não se deduzem adaptações pela presença de personagens.

`start_year` é exibido como ano inicial informado, sem inventar dia/mês. `count_of_episodes` é a quantidade cadastrada na fonte, incluindo zero quando informado, sem tratá-la como catálogo completo ou número de temporadas. Campos ausentes são ocultos. Agents of S.H.I.E.L.D. foi recebido como ID 1, Marvel 31, ano 2013 e 136 episódios cadastrados. Imagens e nomes vêm da API; nenhum nome localizado foi presumido. Busca, ordem e seleção persistem ao navegar e recriar a tela; cache de respostas é persistente.

## Design e validação

Frame Séries 62:338 consultado uma vez com contexto completo e screenshot. Tokens existentes, fontes, fundos, card de destaque de filmes e retratos reutilizados. O card ganhou apenas um ID no rótulo, mantendo o texto padrão Filmes. Controles de 48dp, pôster fitCenter, carrossel fluido e destaque empilhado em tela estreita/fonte ampliada. Contrato visual em `src/theme/series.json`.

A primeira execução [37283118329](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37283118329), commit nativo `a7a47a2f603431782e5ee51e6abf41b2a459bb7f`, aprovou build/lint, Filmes e Séries. Lint: zero erros e 36 avisos, sendo 3 novos avisos `NotifyDataSetChanged` nos adaptadores de séries; os outros 33 já existiam. O card de filmes manteve seu comportamento após receber o ID do rótulo.

Respostas reais de séries: 21 itens iniciais com cursor 300, mais 18 com cursor 500, 14 na ordem descendente com cursor 200 e 2 para busca com capitalização mista de Agents (Agents of S.H.I.E.L.D. e Hulk and the Agents of S.M.A.S.H.). IDs não se repetiram entre os lotes; registros com nomes iguais, como Biker Mice from Mars, têm IDs distintos. Os relatórios online e offline foram idênticos. Batman, busca inexistente e entradas inválidas também foram conferidos.

O emulador aprovou seleção, paginação, busca, retorno/restauração e layouts 320×640, 430×932, 640×1000 e fonte 200%. A conferência final restrita a Séries [37287396195](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37287396195), commit `ff210899d63aee6829f928043c62b91f01a442e3`, também passou. Foram revisadas 21 capturas, incluindo título completo e metadados em pontos de rolagem separados nas telas estreita, ampla e com fonte 200%. As capturas confirmam leitura do título longo, ano, quantidade, ação externa e aviso de catálogo parcial/licenciado. O código da aplicação permanece igual ao da execução nativa aprovada. Evidências da execução final: `android-catalog-check` 11334784643, `android-preview` 11335596210 e `android-lint` 11335074671. Sem testes unitários e sem publicar APK automaticamente.

## Roteiro manual

1. Entrar como visitante e abrir Histórias → Séries Marvel.
2. Conferir o destaque Agents of S.H.I.E.L.D., ano e quantidade cadastrada; abrir a fonte externa quando houver navegador.
3. Percorrer o carrossel pelas capas/setas, carregar mais e observar seleção e contador.
4. Buscar Agents; voltar a Histórias e reabrir Séries, conferindo busca/ordem/seleção.
5. Buscar Batman e um título inexistente; conferir estado vazio. Limpar e selecionar Z–A.
6. Reabrir as mesmas consultas offline após carregá-las online; conferir fontes ampliadas e rolagem até o rodapé.

## Próximo bloco proposto

Detalhes nativos de séries: validar descrição e relações reais, episódios e seus números, sem inventar temporadas ou vínculos com quadrinhos.
