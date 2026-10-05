# Bloco 10 — Detalhes nativos de filme

Rota: Histórias → Filmes Marvel → Detalhes do filme, tanto no destaque quanto no carrossel. Java/MVVM, Fragments, XML e ViewBinding; pôster inteiro, nome original, duração e classificação atribuída à fonte, resumo traduzido, descrição completa sob demanda, personagens/equipes Marvel e créditos com links explícitos à ComicVine.

## Dados e vínculos

A identidade Marvel é confirmada no lote de busca por ID e novamente no detalhe. Personagens e equipes precisam pertencer ao índice canônico Marvel e manter a mesma referência; a hidratação acontece em lotes de até 24, sem consultas por item. Personagens abrem os perfis nativos, com retorno ao filme. Equipes e créditos abrem explicitamente a fonte externa.

As respostas reais do bloco 9 fornecem `studios` com tipo 4010 e `producers`/`writers` com tipo 4040, usando aliases de recursos. O parser verifica IDs, caminhos e URLs antes de construir links. O campo `writers` mistura criadores de HQ e autores de roteiro sem distinguir função: a interface usa **Autores vinculados na fonte**, preservando nomes sem atribuir cargos inventados. Locais, objetos e conceitos são mostrados quando há vínculo verificável.

Duração vem de `runtime`; classificação é o rótulo original da ComicVine, sem assumir classificação brasileira. Distribuidora só aparece quando há texto disponível. Datas permanecem ocultas porque `release_date` repete a atualização; valores monetários foram omitidos porque moeda, território e distinção entre receitas não foram confirmados. Nenhuma adaptação ou filme relacionado foi calculado por coincidência de personagens.

Resumo e descrição passam por HTML seguro → texto → ML Kit pt-BR. Nomes das referências, links e nomes com capitalização no resumo original são protegidos. Respostas e traduções têm cache persistente. Campos ausentes são ocultos; estados de carga/erro usam Marv e permitem tentar novamente.

## Design e validação

Não há frame dedicado de detalhes de filme no mapeamento disponível. A tela reutiliza os componentes e tokens de detalhes de personagem, quadrinho e arco; proveniência em `src/theme/movie-details.json`. Pôster fitCenter, layout fluido, controles de 48dp e rolagem com insets do menu.

Compilação, lint e validação no emulador pendentes. O CI verifica catálogo de filmes, detalhes, tradução/cache offline, exclusão de Batman, vínculos Marvel, perfis e retorno, destaque/carrossel e layouts 320×640, 430×932, 640×1000 e fonte 200%. Os fluxos anteriores não sofreram mudanças de comportamento; sua validação permanece registrada no bloco 9. Nenhum teste unitário e nenhuma publicação automática de APK.

## Roteiro manual

1. Abrir como visitante, entrar em Histórias → Filmes Marvel e abrir Iron Man pelo destaque.
2. Conferir pôster, duração, classificação e resumo em português; abrir descrição completa sob demanda.
3. Abrir um personagem e voltar ao filme; verificar identidade e posição preservadas.
4. Conferir equipes, estúdios, produtores e autores. Os links de equipes e créditos identificam abertura externa.
5. Voltar ao catálogo e abrir o filme selecionado no carrossel; voltar preserva busca, ordem e seleção.
6. Depois de carregar online, desligar a rede e reabrir as mesmas consultas; testar telas pequenas e fonte 200% com rolagem.

## Próximo bloco proposto

Catálogo de séries Marvel: validar suporte real da ComicVine e um critério confiável de vínculo Marvel, reutilizando o frame Séries e os componentes existentes.
