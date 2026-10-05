# Bloco 7 — Catálogo nativo de arcos Marvel

## Resultado e escopo

Histórias → **Explorar arcos de história** abre o catálogo nativo com imagens completas, nomes originais, editora, busca por nome, A–Z/Z–A e carregamento manual por páginas. O botão de cada card identifica **Ver arco na ComicVine ↗** e abre a fonte externa. O próximo bloco proposto é o detalhe nativo do arco; sequência de leitura, descrição e relações pertencem a essa etapa.

A tela nova compõe os tokens/fontes, painel contínuo, menu sobreposto, cabeçalho, campos, botões, cards e estados com ícones já existentes. Não há frame específico de arcos no mapeamento aprovado, e não foram solicitados novos contextos/screenshot/variáveis do Figma. O card vertical conserva a imagem inteira com `fitCenter`, título sem limite de linhas e botões de pelo menos 48dp. Não cria novo asset do Marv. Java/MVVM, Fragments/XML, ViewBinding e SavedStateHandle preservados; sem novas dependências.

## Contrato real e critério Marvel

A [sondagem dirigida](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37243428446) resolveu a editora Marvel e consultou `publisher.story_arcs`: **1.057 referências** no momento da captura. `story_arcs/filter publisher:<id>` retornou os mesmos 12 itens/totais do lote geral, incluindo outras editoras; o produto não utiliza esse filtro ignorado. Dois lotes de 12 IDs retirados do índice Marvel retornaram somente IDs solicitados, caminhos coerentes e editora Marvel.

A inclusão exige **referência no índice da editora + ID/caminho do arco + ID/caminho de publisher Marvel na resposta**. O nome do lote deve coincidir com o índice utilizado para ordenar e buscar. Referência fora do índice não participa da consulta; um registro do índice que responder com outra editora é excluído antes da exibição. Resposta com ID inesperado, duplicado, ausente ou caminho/nome divergente falha como dados inconsistentes. O vínculo é o informado pela ComicVine; o aplicativo não faz uma auditoria externa de direitos editoriais.

| Informação | Fonte | Tratamento |
|---|---|---|
| Editora | `publishers/name:Marvel` → detalhe | Resolução única; identidade Marvel confirmada, sem ID fixado no produto |
| Índice de arcos | `publisher/story_arcs` | IDs, nomes e caminhos canônicos; somente referências leves, sem hidratar o catálogo inteiro |
| Nome/ordem | Nome original no índice e no lote | A–Z/Z–A por título completo, ignorando maiúsculas e com desempate por ID; prefixos de série e pontuação permanecem |
| Busca | Nomes canônicos do índice | Contém o termo, ignora caixa/acentos/espaços externos; não busca descrições nem cria aliases |
| Cards | `story_arcs/filter id:<IDs>` | Lotes de até 12; resposta reconstruída na ordem do índice e editora verificada |
| Imagem | `image.medium_url` | Carregador/cache existentes, imagem completa; campo ausente oculta a imagem |
| Fonte externa | `site_detail_url` | HTTPS ComicVine sem query/credencial, sufixo da identidade conferido; campo inválido oculta o botão |
| Quantidade carregada | Lista de cards verificados | Contagem local, não quantidade de edições nem total de arcos confirmados no mundo |

O campo de quantidade de aparições retornou grafia `count_of_isssue_appearances`, e a seleção com `count_of_issue_appearances` não devolveu esse campo. Este catálogo não exibe uma contagem presumida. As amostras e IDs ficam somente nos scripts/diagnóstico debug. Nomes próprios/títulos permanecem originais; não há novo conteúdo descritivo em inglês na interface a traduzir. Labels e orientações usam pt-BR; ML Kit/cache das telas existentes continuam disponíveis.

## Paginação, estados e persistência

A busca acontece no índice completo de referências Marvel antes da hidratação. O cursor representa referências examinadas dentro dessa busca/ordem; não é offset do catálogo global. Os lotes são montados com IDs canônicos e reconstruídos na ordem local. Quando não houver item elegível, a operação examina até três lotes; se ainda houver referências, **Carregar mais** permite continuar. A mudança de busca/ordem inicia nova sequência e invalida callbacks antigos. O ViewModel deduplica IDs e impede duas páginas concorrentes.

Falha inicial mostra estado com retentativa. Busca vazia tem orientação para outro nome/limpar. Falha de uma nova página conserva cards, cursor e retentativa identificada. Carregamento parcial não apaga a lista. Apenas **Buscar** ou ação de busca do teclado envia a pesquisa; digitar atualiza o rascunho sem gerar consultas por letra.

O ViewModel pertence à Activity: preserva busca aplicada, rascunho, ordem, páginas carregadas e dados ao sair/voltar. SavedStateHandle guarda busca/ordem/rolagem para recriação; os dados são recuperados de cache/rede. A tela registra a rolagem ao destruir a View e tenta restaurá-la após a lista estar pronta. Após encerrar o processo, só a primeira página é recarregada; uma posição de uma página posterior pode ser limitada ao conteúdo disponível. Não promete snapshot transacional da API externa.

Respostas públicas usam o cache persistente anterior (um dia, com recuperação de resposta anterior em falha de rede). Imagens usam o carregador compartilhado. `.env`/Secret provisionam a instalação debug; nenhuma chave entra no APK. Não são publicados APKs automaticamente.

## Arquivos e fluxos

- `ui/arcs/ArcsFragment`, `ArcsViewModel`, `StoryArcAdapter`; `fragment_arcs.xml`, `item_story_arc.xml`, `values/arcs.xml`.
- `CatalogModels.StoryArc/ArcsPage`, `MarvelRepository.arcs/arcsPage` e reconhecimento de `story_arcs` no índice.
- Histórias, Activity e grafo ligam o catálogo; Histórias continua selecionada no menu e voltar retorna à origem.
- `ArcsCheckActivity` somente debug e `.github/scripts/check-arcs.py`; estágio novo no workflow Android.
- Sondagem reaproveita os arquivos de contrato já excluídos do build automático para evitar compilar o app durante a inspeção de dados.

## Verificação

XMLs, Python e diff verificados localmente. A [execução integrada](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37243927597) passou compilação/lint, ML Kit/cache e regressões reais de catálogo, quadrinhos e detalhes. Os dados de arcos online/offline também passaram: duas páginas de 12, busca de três registros Civil War, ordem inversa e exclusões/cursor inválido conferidos. O roteiro visual parou ao alinhar o primeiro título após rolar; a correção altera somente roteiro/escopo de CI. A [execução final](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37245405383) passou compilação/lint, ML Kit/cache e o roteiro completo de arcos. Foram revisadas 14 capturas: catálogo inicial, primeira página, 24 carregados, busca e retorno, busca vazia, ordem inversa, 320×640, 640×1000, fonte 200% e regressão para detalhe de quadrinho. Lint: **0 erros, 30 avisos**. Relatórios online/offline idênticos e ambos com `success=true`. Nenhum APK publicado. Sem testes unitários novos.

O diagnóstico real confere duas páginas, IDs disjuntos, ordem ascendente/descendente, busca Civil War, exclusão de The Killing Joke e cursor inválido. Compara o mesmo conteúdo após matar o processo sem rede. O roteiro visual passou abertura por Histórias, carrega mais, busca, retorna/reabre, verifica busca vazia, ordenação, telas pequenas/grandes e fonte ampliada. Conferiu ainda acesso ao catálogo/detalhe de quadrinhos e retorno à Home após sair dos arcos. A execução intermediária isolada parou na primeira consulta com `NETWORK/HTTP=0`; o roteiro final exige rede validada e estável e permite até duas novas tentativas do diagnóstico online somente quando as falhas registradas no log são exclusivamente NETWORK/HTTP=0. Não repete diagnóstico offline, erro HTTP/limite/credencial ou ausência de conclusão. O produto não recebeu alterações desde a execução integrada; correções posteriores são somente no roteiro e escopo de CI. O CI mantém regressões integradas para mudanças no produto e isola arcos quando apenas seu roteiro/diagnóstico debug é corrigido.

| Critério | Estado |
|---|---|
| 1. Design/componentes | 14 capturas revisadas; componentes existentes reutilizados |
| 2. Navegação/hierarquia | Histórias, busca/retorno e quadrinhos passaram no emulador |
| 3. Regressões | Build/lint e regressões integradas passaram; arcos completos passaram na execução final |
| 4. Telas/fontes | 320×640, 640×1000 e fonte 200% revisados |
| 5. Java/MVVM/XML | Atendido |
| 6. Decisões/pendências | Catálogo aprovado; detalhe nativo separado |
| 7. Estados/recuperação | Busca vazia e cache após reinício conferidos; falha/retry manual pendente |
| 8. Sem testes unitários | Atendido |
| 9. Dados/transformações | Contrato conferido; mapeamento acima |
| 10. Credenciais/propriedade | Chave fora do APK conferida no CI; heróis não aplicável |
| 11. Marvel | Índice/editora conferidos e arco externo excluído no Android |
| 12. Português/cache | Labels pt-BR e títulos originais; conteúdo offline idêntico após reiniciar |
| 13. Criação/edição de herói | Não aplicável |

Aparelho físico, TalkBack, navegador externo e falhas/retry provocados manualmente dependem de validação adicional.

## Roteiro manual

1. Selecionar `codex/bloco-7-arcos` (ou `main` depois da integração), configurar `.env` privado e executar **Sua Marvel (local)**.
2. Explorar sem entrar → Histórias → **Explorar arcos de história**. Conferir imagens, nomes, editora e fonte identificada.
3. Buscar **Civil War**, alternar A–Z/Z–A, limpar e usar **Carregar mais**. Conferir ausência de duplicatas, estados e títulos longos.
4. Voltar a Histórias e reabrir: busca, ordem e lista permanecem. Abrir um arco na ComicVine e retornar ao app.
5. Reiniciar offline com páginas previamente carregadas; conferir cache. Testar fonte ampliada, teclado, tela pequena, TalkBack e falha/retry no aparelho.
6. Histórias → Quadrinhos → **Ver quadrinho**, depois Personagens → perfil/Histórias: os fluxos anteriores continuam acessíveis.

## Próximo bloco proposto

Detalhe nativo de arco Marvel: imagem, nome, editora, descrição traduzida/cache e edições vinculadas, com critério de sequência explicitado após validar o contrato. Não presume ordem oficial de leitura nem relações/temporadas/adaptações inexistentes. Aguarda validação deste catálogo antes da implementação.
