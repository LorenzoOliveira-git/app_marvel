# Bloco 6 — Detalhes nativos de quadrinhos

## Resultado e escopo

**Ver quadrinho** abre o detalhe nativo a partir da Home, dos destaques de Histórias/Quadrinhos, do carrossel, da primeira aparição no perfil e da lista de aparições. Personagens creditados abrem seus perfis; voltar retorna à tela de origem sem substituir as pilhas de navegação. O catálogo mantém volume, ordem e seleção.

A tela mostra capa completa, título composto de volume/número, título original da edição quando informado, volume, data de venda e data da capa em campos distintos. Resumo e descrição usam os campos reais, texto seguro, ML Kit e cache persistente. Seções sem conteúdo ficam ocultas; falhas de tradução e relações mantêm a identidade e a opção de tentar novamente. Não há resumo criado por IA, mock ou data substituída silenciosamente.

Não existe frame específico de detalhe de edição no mapeamento aprovado. Este bloco compõe os componentes e tokens existentes: painel até o rodapé, menu sobreposto, cabeçalho/SUA MARVEL original, fontes Bebas Neue/Inter, cartões, estados com ícones e carrosséis de relações. Capa com `fitCenter`, metadados/controles com altura variável, títulos acessíveis e área de toque mínima de 48dp. Sem consultas adicionais ao Figma, ativos novos do mascote ou dependências estruturais.

Java/MVVM, Fragments, XML, ViewBinding e `SavedStateHandle` mantidos. O ViewModel pertence ao Fragment; argumentos de navegação restauram a edição, e o repositório recupera os dados em cache após recriação. Os carrosséis já existentes conservam seus ViewModels e estado ao retornar. Não promete recuperar toda a rolagem após reinstalar o app.

## Contrato e mapeamento reais

[Sondagem dirigida](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37239545639) consultou duas edições observadas nas etapas anteriores: uma recente sem resumo/descrição e uma histórica com descrição, 19 personagens, cinco créditos de criação, duas equipes e créditos de locais/objetos/conceitos. A editora foi resolvida pelo nome e cada volume confirmado pelo índice Marvel e pelo detalhe do volume. Não há IDs de amostra no código do produto; ficam somente na inspeção/diagnóstico debug.

A amostra histórica contém a equipe **Martians**, vinculada na ComicVine à editora **In the Public Domain**. Ela não entra no carrossel Marvel. A sondagem confirma a forma dos campos recebidos nas amostras; não garante dados completos para todas as edições.

| Informação | Campo/consulta | Tratamento |
|---|---|---|
| Identidade da edição | `issues/id` → `api_detail_url` → detalhe | Exige resultado único, ID e caminho coerentes; ID recebido pela navegação |
| Volume/Marvel | `publisher.volumes`, `issue.volume`, detalhe do volume | ID/caminho canônico e editora Marvel verificados antes da exibição |
| Título | `volume.name` + `issue_number` | Identificador composto; título original, sem localização oficial presumida |
| Nome da edição | `name` | Original opcional, separado do título composto |
| Capa | `image.medium_url` | Carregador/cache existentes; capa inteira com `fitCenter` |
| Data de venda | `store_date` | Label explícito e formatação pt-BR; não usa atualização do registro |
| Data da capa | `cover_date` | Label explícito separado; datas ausentes/inválidas ficam ocultas |
| Resumo/descrição | `deck` / `description` | Seções independentes; HTML convertido em texto, `script/style` removidos; tradução automática identificada |
| Personagens | `character_credits` | Relação direta + índice Marvel + detalhe com editora; perfis internos |
| Equipes | `team_credits` | Relação direta + índice Marvel + editora do resultado; links externos explícitos |
| Autores | `person_credits` | Nome/ID/caminho e vínculo desta edição; cargo informado quando mapeado |
| Arcos, locais, objetos e conceitos | `story_arc_credits`, `location_credits`, `object_credits`, `concept_credits` | Créditos diretos da edição confirmada; nomes originais e links ComicVine validados; não afirma editora própria para entidades neutras |
| Fontes | `site_detail_url` da edição/volume/créditos | HTTPS ComicVine sem query/credencial; créditos conferem sufixo da identidade; destinos externos identificados |

Os papéis de criação conhecidos são apresentados como Arte, Capa, Roteiro, Arte-final, Desenho, Letreiramento, Cores e Edição. Papéis desconhecidos não recebem função inventada; o nome permanece nos créditos. O catálogo nativo de arcos, filmes e séries continua fora deste bloco: arcos creditados têm somente link identificado para a fonte. Ambas as amostras desta sondagem têm `story_arc_credits` vazio; não há validação visual de uma edição com arco informado.

A descrição preserva os nomes canônicos presentes nos créditos, volume/título e textos de links do próprio HTML. Não inventa aliases nem garante localização oficial dos nomes. O HTML original fica no modelo/cache; a tela utiliza texto sem links executáveis. O cache de tradução utiliza entidade, campo, hash do original e versão; mudar a descrição não reutiliza tradução de texto antigo. A proteção/tradução de personagens conserva a versão de cache já usada no bloco anterior.

## Estados, paginação e credenciais

Identidade, resumo, descrição e cada grupo de relações têm estados separados. Seção ausente usa `UNAVAILABLE`; falha real usa ícone e retentativa; falha de uma nova página conserva itens e cursor. Personagens/equipes usam lotes e paginação existente, deduplicação por ID e validação Marvel a cada página. Listas de créditos externos vêm do próprio detalhe e não iniciam hidratação de todo o catálogo.

Callbacks são invalidados por geração ao recarregar/fechar o ViewModel; observadores pertencem ao ciclo de vida da View. Os adapters de relações são desconectados ao destruir a tela. Insets são reaplicados para preservar acesso ao conteúdo atrás do menu sobreposto.

A ComicVine e o cache público reutilizam o transporte direto e o armazenamento privado anteriores. `.env`/Secret provisionam a instalação debug; a chave não entra no APK. O fluxo de execução local continua **Sua Marvel (local)** / `:app:runLocalDebug`. Nenhum APK é versionado ou publicado automaticamente.

## Arquivos principais

- `ui/issues/IssueDetailsFragment` e `IssueDetailsViewModel`; `fragment_issue_details.xml`, `component_issue_credit.xml`, `values/issue_details.xml`.
- `CatalogModels.IssueDetails/Credit`, `MarvelRepository.issueDetails/issueRelations`, tradução protegida em `CatalogDescriptions`.
- `MainActivity.openIssue`, grafo e pontos de entrada em Home, Histórias, perfis e catálogo.
- `IssueCheckActivity` somente debug, `.github/scripts/check-issue.py`, compilação/lint/roteiros nativos no workflow.

## Verificação

XML, Python e diff verificados localmente. A [execução integrada](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37240382356) compilou Java, passou lint (0 erros, 30 avisos), ML Kit/cache e os roteiros reais de catálogo e quadrinhos. Também passou os diagnósticos de detalhes online/offline; o roteiro visual parou ao procurar um título fora da área visível. A correção afeta somente o roteiro, o diagnóstico debug e o escopo de CI. Sem testes unitários novos. A [execução final, tentativa 2](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37242184616/attempts/2) passou compilação/lint, ML Kit, diagnóstico online/offline e todas as rotas visuais. A tentativa 1 parou em `NETWORK/HTTP=0` antes da navegação; a repetição do mesmo commit passou. Nenhuma alteração adicional no produto foi necessária.

O diagnóstico usa somente respostas reais, verifica identidade e volume Marvel, rejeição de edição externa/ID inválido/equipe externa, personagens ligados à edição, tradução e nomes preservados. Mata o processo, desliga a rede e compara os mesmos dados/descrição com o cache. O roteiro UI passou entrada por Home, catálogo, primeira aparição e história; abertura de personagem creditado; retorno às origens; capturas em 320×640, 640×1000 e fonte 200%. Foram revisadas as 14 capturas finais: capa completa, metadados e créditos com quebra de linha, descrição rolável, menu sobreposto e perfil relacionado. Ambos os relatórios de dados retornaram `success=true`; 18 personagens Marvel foram conferidos e a descrição offline veio do cache após matar o processo.

| Critério | Estado |
|---|---|
| 1. Design/componentes | Capturas revisadas; componentes existentes preservados |
| 2. Navegação/hierarquia | Rotas e retornos passaram no emulador |
| 3. Regressões | Build/lint e regressões de catálogo/quadrinhos passaram; detalhes passaram na execução final |
| 4. Telas/fontes | 320×640, 640×1000 e fonte 200% conferidos |
| 5. Java/MVVM/XML | Atendido |
| 6. Decisões/pendências | Escopo aprovado e diferenças documentadas |
| 7. Estados/recuperação | Dados/tradução offline conferidos; falhas de rede/retry no aparelho físico pendentes |
| 8. Sem testes unitários | Atendido |
| 9. Dados/transformações | Atendido na sondagem; mapeamento acima |
| 10. Credenciais/propriedade | Chave fora do APK conferida pelo CI; heróis não aplicável |
| 11. Marvel | Contrato e rejeição de edição/equipe externas e ID inválido conferidos no Android |
| 12. Português/cache | ML Kit real e cache persistente após reinício conferidos |
| 13. Criação/edição de herói | Não aplicável |

Ainda dependem de aparelho físico: TalkBack, integração com navegador externo e retentativas de falhas de rede em cada seção. As amostras não têm arcos creditados; a tela de créditos de arco não foi validada com uma lista preenchida. A tradução é automática e pode conter construções pouco naturais; nomes protegidos permanecem originais.

## Roteiro manual

1. Usar `codex/bloco-6-detalhes-quadrinho` (ou `main` após integrar), configurar `.env` privado e executar **Sua Marvel (local)**.
2. Home → **Ver quadrinho**; conferir capa, título, volume e datas. Testar edição sem descrição: nenhuma seção em carregamento permanente.
3. Histórias → Quadrinhos → selecionar volume/Antigos e avançar no carrossel → **Ver quadrinho** → voltar: volume, ordem e seleção continuam.
4. Personagens → Spider-Man → primeira aparição → **Ver quadrinho**; conferir descrição traduzida, créditos e relações. Abrir um personagem creditado e voltar ao mesmo quadrinho.
5. Perfil → Ver aparições → primeira edição ou edição da linha do tempo → **Ver quadrinho**; retornar à lista.
6. Com dados previamente carregados, reiniciar offline. Conferir fonte ampliada, tela pequena, leitor de tela e falhas de rede/retentativas no aparelho físico. Links de fonte/volume/créditos devem abrir seus destinos corretos no navegador.

## Próximo bloco proposto

Catálogo nativo de arcos de história Marvel: validar as relações da ComicVine e desenhar a navegação com os componentes existentes, antes de desenvolver o detalhe do arco. Aguarda validação deste bloco; filmes/séries e criação de heróis continuam no plano posterior.
