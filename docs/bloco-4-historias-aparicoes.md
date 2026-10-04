# Bloco 4 — Histórias e aparições

## Resultado e escopo

Histórias passa a ter conteúdo real e abre as aparições do Spider-Man em destaque. O botão **Ver aparições** nos perfis abre a mesma tela para o personagem selecionado. **Ver perfil** retorna à navegação de perfis existente; voltar mantém a pilha anterior, busca e seleção do catálogo. Java 11, MVVM, Fragments, Navigation e XML, sem novas dependências estruturais ou backend.

Frames `64:632` e `59:126` consultados uma vez cada com contexto e imagem. Referência em `src/theme/history.json`. Identidade com texto à esquerda e imagem à direita, seção da primeira edição e lista vertical com nós/linha reutilizam cores, fontes, logo, painel e componentes existentes. Em largura abaixo de 360dp ou fonte acima de 130%, a identidade é empilhada e o cabeçalho ganha segunda linha. As capas na lista usam `fitCenter` para permanecer completas. O painel chega ao rodapé, com menu sobreposto e espaço de rolagem abaixo dos controles. Não há Marv nos estados comuns destas telas.

A exportação do nó `60:242` foi solicitada isoladamente, mas os downloads PNG/SVG devolveram uma página “Site Unavailable”. O marcador usa uma forma Android de 24dp; não se declara reutilização binária desse asset. Logo e ícones já existentes continuam usando os assets locais do projeto. Essa diferença de origem deve ser considerada na avaliação de fidelidade.

Esta entrega cobre destaque, identidade/resumo, primeira aparição e aparições em edições Marvel. Os catálogos completos de quadrinhos, filmes, séries, arcos e suas relações ficam em blocos posteriores. Por isso os atalhos correspondentes da referência não são expostos sem destino implementado. A seção **Conheça as histórias** apresenta HQs recém-publicadas verificadas, reutilizando o catálogo existente. Edições abrem a página real da ComicVine com destino externo explícito.

## Contrato e mapeamento

Sondagem primária com a API real: [contrato de aparições](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37229437323). O filtro `issues/` com `characters:<id>` foi ignorado: as respostas incluíram Popeye e o total global de edições. O campo `character_credits` não veio na listagem, embora exista no detalhe de edição. Nenhum desses recursos é usado para presumir a relação.

O vínculo `character.issue_credits` retornou 18.142 referências do destaque na sondagem, em resposta de aproximadamente 4,1 MB. Nomes nulos nesses vínculos são válidos; o título é obtido posteriormente da edição e volume. Duas consultas por IDs, de 12 referências cada, devolveram somente os IDs solicitados, com 12 volumes Marvel verificados em cada lote. Esse resultado é uma amostra; a aplicação confere todas as edições efetivamente exibidas.

| Informação | Fonte | Validação e apresentação |
|---|---|---|
| Destaque e identidade | `MarvelRepository.featured/details` existentes | Nome/ID e publisher Marvel conferidos; destaque fixo, sem seleção aleatória |
| Resumo e origem | `deck`, `origin.id/name` | ML Kit sob demanda, cache existente e proteção de nomes/aliases; original preservado |
| Primeira aparição | `first_appeared_in_issue` | Vínculo recebido, edição por ID e volume presente em `publisher.volumes`; não inferida pela menor data |
| Aparições | `character.issue_credits` | Perfil resolvido no índice canônico Marvel; ID, nome e publisher do detalhe conferidos; URLs/IDs do vínculo validados e deduplicados |
| Edições de uma página | `issues/`, filtro por IDs | Cada ID e URL precisam corresponder ao vínculo solicitado; volume precisa corresponder a ID/URL do índice Marvel |
| Título | `volume.name` + `issue_number` | Composição explícita; sem tradução oficial inventada |
| Data | `cover_date` | Identificada como **Data da capa**, opcional; não representa venda/publicação nem atualização do registro |
| HQs recém-publicadas | Repositório `recent` existente | `store_date`, até a data atual, e volume Marvel verificado |
| Capas e links | `image.medium_url`, `site_detail_url` | Componente de imagens existente e links HTTPS ComicVine validados |

As aparições preservam a sequência de `issue_credits`; **não são apresentadas como cronologia global nem ordem oficial de leitura**. A legenda da tela identifica essa sequência. A amostra inclui datas de capa futuras (ex.: novembro de 2026), portanto a lista não chama todas as aparições de edições já publicadas. Não mistura esse critério com as HQs recém-publicadas da seção Histórias.

## Paginação, cache e falhas

- Índice solicitado somente ao abrir as aparições do personagem. Não traduz nem hidrata o catálogo inteiro.
- Lotes de 12 referências, até três lotes numa operação quando nenhum item Marvel puder ser apresentado. Cursor avança sobre referências examinadas; itens são deduplicados no ViewModel e a ordem do vínculo permanece intacta.
- Próxima página manual. Falha preserva os cards anteriores e o cursor; o botão permite retentativa, sem chamadas duplicadas por toques repetidos.
- Respostas usam o cache público persistente existente, TTL de um dia e recuperação de resposta anterior em falha de rede. Original/tradução e imagens usam seus caches existentes.
- Limite de resposta ampliado para 8 MB **somente** no detalhe `character/` com o campo exato `id,name,publisher,issue_credits`; demais detalhes mantêm 2 MB. Arquivo público separado do SQLite evita `CursorWindow` para esse índice.
- Identidade, resumo, origem, primeira edição e aparições têm estados independentes. Seção da primeira aparição é oculta quando ausente; falhas oferecem retentativa. Ausências não geram fatos, anos ou conteúdo fictício.
- Nenhuma credencial em código/APK/logs; provisão privada já existente. Nenhum dado de conta enviado à tradução. Nenhum mock ou teste unitário adicionado.

## Arquivos e fluxos

`ui/history/`: `StoriesFragment`, `CharacterHistoryFragment`, `CharacterHistoryViewModel`, `HistoryIdentityBinder`. Layouts `fragment_stories`, `fragment_character_history`, `component_history_identity`, `item_appearance`; recursos `history.xml` e nó de linha do tempo. `AppearanceAdapter` e extensão opcional de `RecentIssueAdapter` reutilizam carregamento de imagem, cards e links.

`MainActivity`/grafo conectam Histórias, aparições e perfis; o perfil recebeu um botão funcional. `CatalogModels`/`MarvelRepository` acrescentam índice/páginas verificadas. O transporte só amplia o limite do índice específico. Diagnóstico debug e roteiro ADB verificam serviços reais, cache e navegação.

## Verificação

Sintaxe Java 11, XML, Python e diff conferidos localmente. [Execução nativa final](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37230695628), código `7f1fcfb3e7910950cbb573b1d6f7b30881661c9f`: **sucesso**. `assembleDebug`/`lintDebug`, provisionamento privado, ML Kit, diagnóstico de catálogo e roteiro de navegação passaram. Lint: zero erros/falhas fatais e 29 avisos; nenhum teste unitário executado.

No emulador Android 35, as duas páginas de 12 aparições tiveram cursores 12/24, IDs disjuntos e posições compatíveis com o vínculo do personagem. O índice de 18.142 referências, edições, primeira aparição, perfil, relações, resumo e poderes retornaram idênticos após reinício offline; traduções retornaram do cache. IDs inválidos e de outra editora continuaram rejeitados. A verificação do transporte confirmou ausência da chave no APK.

Foram produzidas 37 capturas. Inspeção das telas novas incluiu destaque, primeira edição, capas completas, página seguinte, perfil de outro personagem, fonte 200%, 320×640 e 640×1000. O gradiente do botão de história foi conferido nas capturas finais. O roteiro passou por Histórias → aparições → perfil → voltar, perfis do catálogo → aparições → voltar e restauração de busca/seleção, além dos fluxos existentes da Home, filtros e erro comum. Evidências sanitizadas nos artefatos da execução por sete dias. Documentação e referência JSON atualizadas depois da verificação, sem alterar o código Android validado.

| Critério | Estado | Evidência/limite |
|---|---|---|
| 1. Design/componentes | Atendido com adaptação declarada | Capturas comparadas aos dois frames; recursos compartilhados, botão em gradiente e marcador nativo conforme limitação acima |
| 2. Navegação/hierarquia | Atendido no emulador | Histórias → aparições ↔ perfil; perfil do catálogo → aparições; voltar restaura a tela anterior |
| 3. Regressões | Atendido no escopo verificado | Home, busca/filtros, perfis e retorno ao catálogo passaram no roteiro final |
| 4. Telas/fontes | Atendido no emulador | Capturas 430×932, 640×1000, 320×640 e fonte 200%; texto e controles acessíveis por rolagem |
| 5. Java/MVVM/XML | Atendido | Sem Compose ou mudança estrutural de dependências |
| 6. Decisões/pendências | Atendido | Escopo aprovado; catálogos/arcos separados; ordem e datas documentadas |
| 7. Estados/recuperação | Atendido no escopo verificado | Estados compartilhados e erro comum conferidos; preservação/retentativa de próxima página implementadas; rede intermitente em aparelho físico ainda depende do roteiro manual |
| 8. Sem testes unitários | Atendido | Compilação, lint e diagnóstico ADB real |
| 9. Dados/transformações | Atendido | Sondagem e mapeamento acima; sem mocks |
| 10. Credenciais/propriedade | Atendido / heróis não aplicável | Provisão privada e ausência da chave no APK conferidas no CI |
| 11. Marvel | Atendido no escopo verificado | Contrato e repositório no Android: perfil canônico, IDs/URLs de edições e volumes verificados por item |
| 12. Português/cache | Atendido no escopo verificado | Interface/resumo/origem em português; páginas e traduções recuperadas após reinício offline; nomes e títulos preservados como fonte |
| 13. Criação/edição de herói | Não aplicável | Fora deste bloco |

## Roteiro manual

1. Abrir `codex/bloco-4-historias-aparicoes` e executar **Sua Marvel (local)** com o `.env` privado configurado.
2. Explorar sem entrar → **Histórias** → **Ver história**. Conferir nome, retrato, resumo/origem, **Onde tudo começou** e datas da capa.
3. Rolar as aparições, carregar mais e abrir uma edição na ComicVine. **Ver perfil** abre o perfil certo; voltar retorna à história e depois à área Histórias.
4. Em Personagens, buscar e selecionar outro personagem → **Saiba mais** → **Ver aparições**. Voltar duas vezes deve preservar busca/seleção.
5. Após carregar duas páginas, reiniciar sem rede e conferir recuperação dos mesmos dados. Verificar fonte ampliada, tela pequena e botões acima do menu.

Validação em aparelho físico, leitor de tela e abertura no navegador instalado ficam com o usuário. O emulador não substitui esses ambientes.

## Próximo bloco proposto

Catálogo nativo de quadrinhos Marvel, com busca/paginação, relação com volumes e acesso aos detalhes da edição. Conferir apenas o frame e contrato correspondentes; aguardar validação deste bloco antes de implementar.
