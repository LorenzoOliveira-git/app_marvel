# Bloco 5 — Catálogo nativo de quadrinhos

## Resultado e escopo

**Histórias → Explorar quadrinhos** abre o catálogo nativo: destaque real, carrossel de capas completas, seleção de volume com busca, Recentes/Antigos e paginação manual. Java, MVVM, Fragments e XML; sem novas dependências estruturais, backend ou testes unitários.

Frame `62:542` consultado uma vez com screenshot. Reutiliza logo original, menu sobreposto, painel até o rodapé, fontes, gradiente e componentes existentes. Referência em `src/theme/comics.json`. O destaque usa capa de 134×203dp e título de 24sp; carrossel central com fração de largura de 60%, proporção 1,47 e título de 40sp. Em tela pequena/fonte ampliada o destaque empilha; nome completo do volume selecionado aparece abaixo dos filtros. Controles têm altura variável e área de toque mínima de 48dp. Não adiciona Marv aos estados comuns.

**Volume** ocupa o terceiro filtro do frame, pois este bloco utiliza o vínculo real com volumes, não uma saga presumida. A busca no seletor usa nomes canônicos Marvel, sem distinguir acentos/maiúsculas. Filmes, séries e arcos continuam separados. Os botões **Na ComicVine ↗** abrem a página da edição; detalhe nativo de edição é a proposta seguinte.

## Mapeamento e contrato real

[Sondagem dirigida final](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37234828239): três volumes obtidos do índice canônico Marvel foram consultados por `issues/volume`, combinando intervalo e ordenação de `store_date`. Cada consulta de 12 edições, nos offsets 0/12 e nas duas ordens, devolveu somente volumes solicitados. O conjunto amostrado tinha 67 edições com data de venda. A data de 30/09/2026 tinha 123 registros: lotes de 100/23 ordenados por ID foram disjuntos, com 20 edições Marvel verificadas no primeiro lote. Isso valida as amostras, e a aplicação continua conferindo cada resposta utilizada.

| Informação | Fonte | Critério |
|---|---|---|
| Editora | `publishers/name:Marvel` + detalhe | Resolução única de nome, ID e URL; sem ID Marvel fixado no produto |
| Volumes/opções de busca | `publisher.volumes` | Referências com ID, nome e caminho válidos; busca local no índice, sem hidratar todo o catálogo |
| Edições | `issues/` | ID/caminho da edição e ID/caminho do volume conferidos; volume precisa constar no índice Marvel |
| Volume selecionado | `issues/filter volume:<id>` | Seleção precisa ser canônica; cada edição recebida precisa corresponder ao ID selecionado |
| Título | `volume.name` + `issue_number` | Composição identificada, títulos originais preservados; sem localização oficial inventada |
| Recentes/Antigos | `store_date:desc/asc` | Intervalo de 1900 até hoje; valida ordem e limites de data; não usa data de atualização nem data de capa |
| Destaque | `recent()` existente | Primeira edição da seleção recente verificada; independente do filtro do carrossel |
| Capa | `image.medium_url` | Carregador/caches existentes; capas completas com `fitCenter` no carrossel |
| Página da edição | `site_detail_url` | Link HTTPS ComicVine validado e destino externo explícito |

O catálogo deste bloco mostra **edições com `store_date` no intervalo**, não afirma incluir todas as edições históricas sem data de venda. Não há dados mocados, fatos inventados ou descrições em inglês a traduzir neste novo fluxo; os nomes e títulos identificam os registros originais. A tradução/cache das telas anteriores permanece intacta.

## Paginação, estados e persistência

A ComicVine devolveu IDs repetidos entre offsets globais quando várias edições tinham a mesma data de venda. A sondagem adicional também mostrou que `sort:store_date:desc,id:desc` acabava ordenando apenas pelo ID; esse formato não é usado no produto.

A paginação começa por uma janela em ordem de data (até 100 registros no catálogo geral ou 12 no volume selecionado). Grupos de datas inteiramente contidos nessa janela são consumidos; a última data, que pode ter sido cortada, é consultada separadamente com intervalo exato e ordenação por ID. O cursor guarda data, offset do lote diário e número de registros efetivamente consumidos. Depois de esgotar o dia, a próxima janela usa limite de data exclusivo. Assim, empates não dependem da ordem arbitrária da API. Todos os itens válidos dos grupos/lotes consumidos são preservados; a quantidade apresentada por operação pode variar.

A operação examina até três janelas/lotes quando não houver item Marvel. Um lote vazio com fonte ainda a consultar permite **Carregar mais**. Depois do último dia pode haver uma consulta final vazia para confirmar o encerramento; os cards acumulados permanecem. Os cursores e a ordem são verificados contra a fonte real, sem prometer um snapshot transacional de uma API externa mutável.

O ViewModel deduplica IDs e impede carregamentos concorrentes. Falha de nova página mantém itens/cursor e oferece retentativa. O transporte direto executa uma única retentativa automática para falha de rede e HTTP 408/502/503/504, mantendo fila, intervalo e reserva de limite; não repete erro de credencial, HTTP 429 ou resposta inválida. Troca de volume/ordem invalida callbacks antigos e inicia nova sequência. Destaque e catálogo têm estados independentes de carregamento, vazio, falha e conteúdo, com os ícones compartilhados.

Dados públicos usam o cache persistente de um dia e recuperação de resposta anterior quando a rede falha. O ViewModel do catálogo pertence à Activity, preservando itens, seleção, ordem e volume ao sair/voltar na navegação. `SavedStateHandle` restaura volume, ordem e texto da busca após recriação do processo; as páginas são recarregadas do cache/rede. Não promete restaurar todas as páginas acumuladas após encerramento do processo.

## Arquivos e fluxos

`ui/comics/ComicsFragment` e `ComicsViewModel`, `IssueCoverAdapter`, `fragment_comics.xml`, `values/comics.xml`. `CatalogModels.ComicsPage` e métodos `volumes/comics` do repositório. Histórias, Activity e grafo conectam a nova tela, mantendo Histórias selecionada no menu. O card recente recebeu metadado opcional de volume; os outros usos mantêm sua apresentação.

`ComicsCheckActivity` existe somente em debug e não aparece no menu. Roteiro ADB verifica respostas reais, dois cursores, seleção por volume, ordenação, rejeição de volume externo, reinício offline e navegação. Nenhum APK é publicado pela execução automática; artefatos são relatórios/capturas.

## Verificação e checklist

Sintaxe Java 11, XML, Python e diff verificados localmente. A [execução nativa de quadrinhos](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37236470229) confirmou compilação/lint, ML Kit, respostas reais, cache após reinício offline, busca de volumes, Recentes/Antigos, paginação, navegação e capturas de 320×640, 640×1000 e fonte 200%. Lint: **zero erros e 30 avisos**, incluindo um aviso de atualização ampla do novo adaptador (`NotifyDataSetChanged`); não bloqueia a execução.

Os relatórios registraram 22/17 edições nas páginas gerais e 12/12 no volume selecionado, sem IDs repetidos, com ordem por data preservada entre páginas e conteúdo idêntico após reiniciar sem rede. Rejeição de volume externo confirmada. Capturas finais revisadas: destaque, carrossel, seletor, retorno à seleção e layouts pequenos/com fonte ampliada.

Nessa execução, o roteiro de regressão do catálogo concluiu os diagnósticos reais e offline, mas perdeu temporariamente o ADB durante a navegação. O roteiro passou a aguardar a reconexão e repetir o comando até duas vezes, com tempo limitado; falhas persistentes continuam encerrando a verificação. Na [execução final](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37238394306), compilação/lint, ML Kit e a regressão de catálogo, dados reais, cache offline e navegação passaram. O último roteiro de quadrinhos também terminou com sucesso, incluindo dados reais, reinício offline, busca, ordenação, paginação e navegação. **Execução completa aprovada no commit `c9791f3`**; a atualização seguinte inclui somente documentação e referência de design. Nenhum APK foi publicado automaticamente.

Aparelho físico, Windows/Android Studio, TalkBack e falhas intermitentes provocadas manualmente continuam pendentes de validação. Não há afirmação de validação nesses ambientes.

| Critério | Estado | Evidência/limite |
|---|---|---|
| 1. Design/componentes | Atendido no emulador | Capturas revisadas; componentes/ativos compartilhados reutilizados; adaptações acima |
| 2. Navegação/hierarquia | Atendido no emulador | Histórias → Quadrinhos; voltar preserva volume, ordem e seleção |
| 3. Regressões | Atendido no emulador | Home, Personagens, detalhes e Histórias; dados reais, tradução, cache e navegação passaram na execução final |
| 4. Telas/fontes | Atendido no emulador | Capturas de 320×640, 640×1000 e fonte 200% revisadas |
| 5. Java/MVVM/XML | Atendido | Sem Compose/dependências novas |
| 6. Decisões/pendências | Atendido | Catálogo aprovado; filtro real e detalhes externos identificados |
| 7. Estados/recuperação | Atendido no fluxo validado; retentativa manual pendente | Estado inicial/append, geração e preservação implementados; falha/retry intermitente exige roteiro físico |
| 8. Sem testes unitários | Atendido | Apenas compilação, lint e diagnóstico real |
| 9. Dados/transformações | Atendido no contrato | Mapeamento acima; nenhum mock |
| 10. Credenciais/propriedade | Atendido / heróis não aplicável | Fluxo local e ausência da chave no APK conferidos pelo CI |
| 11. Marvel | Atendido no contrato e Android | IDs/caminhos canônicos por edição e rejeição de volume externo |
| 12. Português/cache | Atendido no emulador | Labels em português; títulos originais; ML Kit e dados públicos após reinício offline |
| 13. Criação/edição de herói | Não aplicável | Fora deste bloco |

## Roteiro manual

1. Selecionar `codex/bloco-5-quadrinhos`, executar **Sua Marvel (local)** com `.env` privado configurado e explorar sem entrar.
2. **Histórias → Explorar quadrinhos**. Conferir destaque, capas completas, título/data e menu sobreposto.
3. **Volume**, buscar uma série, selecionar. Conferir edições do volume; alternar **Recentes/Antigos**, usar anterior/próximo e **Carregar mais**.
4. Voltar a Histórias e abrir Quadrinhos novamente: volume, ordem e seleção continuam. **Na ComicVine ↗** deve abrir a edição correta no navegador.
5. Após carregar páginas, reiniciar sem rede para conferir os dados em cache. Conferir fonte ampliada/tela pequena, erro/retry com rede intermitente e leitor de tela no aparelho físico.

## Próximo bloco proposto

Detalhe nativo de edição Marvel: consultar o contrato e criar uma tela coerente com os componentes aprovados, contendo capa, datas corretamente identificadas, resumo/descrição em português com ML Kit/cache e vínculos reais disponíveis. Confirmar a proposta antes de implementar, pois não há um frame específico de edição no mapeamento atual.
