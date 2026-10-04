# Bloco 3 — Detalhes de personagem

## Objetivo e escopo

Abrir um perfil nativo pelos botões da Home e do catálogo, consultar a ComicVine e apresentar identidade, resumo, poderes e relações verificadas. Referência visual: frame `42:478` do arquivo Marvel — Mobile. Uma única consulta de contexto incluiu a imagem; os ajustes usam essa referência salva.

Java 11, MVVM, Fragments, Navigation e XML, usando SDKs/dependências já existentes. `CharacterDetailsViewModel` mantém dados e cursores por perfil; o Fragment observa os estados pelo ciclo de vida da View. As requisições e o cache ficam em `MarvelRepository`; ML Kit traduz localmente.

## Fluxos

- Home → Ver mais → perfil do destaque.
- Personagens → Saiba mais → perfil da seleção; voltar preserva busca, filtros e seleção no catálogo existente.
- Aliados/inimigos → Saiba mais → outro perfil; voltar retorna ao perfil anterior.
- Equipes e primeira aparição abrem suas páginas reais na ComicVine, com indicação explícita do destino externo. Seus detalhes nativos não fazem parte deste subbloco.
- Mais equipes/aliados/inimigos carregam páginas progressivamente; IDs são deduplicados. Uma falha ao carregar a próxima página preserva os cards e oferece retentativa.

O bloco escuro chega à parte inferior do celular e o menu o sobrepõe. O conteúdo tem espaço de rolagem para passar acima do menu. Imagem ampla, identidade em painel arredondado, títulos Bebas Neue, estatísticas e carrosséis seguem a referência. Inter no resumo melhora a leitura; as estatísticas ficam empilhadas em tela pequena ou fonte acima de 130%. Não há Marv nos estados comuns deste perfil.

O resumo corresponde ao campo `deck`, como o texto curto no frame. A biografia extensa, a lista completa de aparições e a área de histórias ficam para o próximo bloco aprovado. A quantidade é a contagem informada pela ComicVine, não uma contagem de edições filtradas pelo aplicativo.

## Mapeamento e ausência de campos

| Informação | Fonte/obtenção | Natureza | Ausência |
|---|---|---|---|
| Editora Marvel | `publishers/`, nome exato; detalhe `publisher/` valida nome/ID | Direta, identidade resolvida | Falha comum com retentativa |
| Perfil, nome, nome verdadeiro, imagem | URL `character/` recebida no índice `publisher.characters`; `id,name,real_name,image,publisher` | Direta; ID e editora conferidos | Nome/identidade inválida rejeita perfil; nome verdadeiro opcional oculto |
| Descrição | `character.deck`, HTML convertido em texto e tradução ML Kit | Direta + tradução automática | Seção oculta; falha de tradução oferece retentativa |
| Origem | `character.origin.id/name` | Direta + tradução do rótulo, ID preservado | Rótulo oculto; retentativa se tradução falhar |
| Poderes | `character.powers`, referências de `power/` | Direta + rótulos traduzidos | Seção oculta; falha de tradução isolada |
| Quantidade de aparições | `character.count_of_issue_appearances` | Direta; contagem da ComicVine | Card oculto; zero válido |
| Primeira aparição | `character.first_appeared_in_issue.id`; `issues/` por ID; volume precisa constar em `publisher.volumes` | Vínculo direto + associação verificada | Seção oculta se ausente/não Marvel; retentativa em falha |
| Título e data da primeira edição | `issue.volume.name`, `issue_number`, `cover_date` | Título composto; data da capa, identificada como tal | Número/data ausentes não inventados |
| Equipes | `character.teams` ∩ `publisher.teams`; `teams/` por IDs e `publisher.id` conferido em cada item | Relação direta, filtrada | Seção oculta se vazia; falha isolada |
| Aliados/inimigos | `character_friends/character_enemies` ∩ `publisher.characters`; `characters/` por IDs e `publisher.id` conferido | Relação direta, filtrada e paginada | Seção oculta se vazia; próxima página disponível quando necessária |
| Links externos | `site_detail_url` HTTPS da ComicVine, sem credencial/query | Direta | Link oculto/desabilitado |

A API observada usa `first_appeared_in_issue/4000-ID/` no vínculo da primeira edição. Não se presume que esse alias funcione como endpoint: a aplicação consulta `issues/` por ID e valida o resultado. Não há busca pela edição de data mais antiga para inventar uma estreia.

Sondagem real: [execução de contrato](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37220921414). Foram observados campos de relações e primeira edição, além de uma equipe sem publisher; esta última é excluída. Nomes de personagens, equipes e títulos permanecem como recebidos, sem localização oficial inventada. O resumo reutiliza a proteção de nome/nome verdadeiro/aliases recebidos e o cache da Home. Original e tradução ficam separados.

Respostas usam o cache persistente existente, TTL de um dia e recuperação de resposta anterior em falha de rede. Traduções de poderes recebem o contexto “Superpower:” para evitar interpretações de palavras isoladas; o prefixo é retirado da apresentação. As chaves incluem ID/campo/hash, idioma e versão do ML Kit. Nenhum dado fictício ou mock é usado como conteúdo do produto.

## Arquivos principais

- `ui/details/CharacterDetailsFragment.java`, `CharacterDetailsViewModel.java` e `fragment_character_details.xml`.
- `CatalogModels.java`, `MarvelRepository.java`: perfil, primeira aparição e relações paginadas.
- `RelatedCharacterAdapter.java`, `item_related_character.xml` e componente de seção compartilhado.
- `MainActivity.java`, `main_graph.xml`, Home e Characters: navegação e voltar.
- `res/values/details.xml`, novos fundos e `src/theme/character-details.json`: conteúdo/estilo.
- Diagnóstico exclusivamente debug e roteiro ADB: repositório real, cache após reinício, telas e navegação. Nenhum teste unitário adicionado.

## Validação

Compilação/lint e emulador em andamento. Evidências finais serão registradas após a execução.

## Roteiro para validar no Android Studio

1. Abrir a branch `codex/bloco-3-detalhes-personagem`; iniciar o emulador e executar a configuração **Sua Marvel (local)** para provisionar a chave privada já configurada no `.env` da raiz.
2. Explorar sem entrar; na Home, abrir **Ver mais** do destaque e conferir identidade, descrição, primeira aparição e poderes.
3. Rolar equipes, aliados e inimigos; abrir um aliado e voltar ao perfil. Carregar outra página de relações.
4. Voltar; abrir Personagens, buscar um nome, abrir **Saiba mais** e voltar. Conferir a busca e a seleção.
5. Com dados já carregados, reiniciar offline e repetir a abertura; testar fonte ampliada e links externos em seu dispositivo.

Aparelho físico, leitor de tela e abertura pelo navegador instalado dependem da validação do usuário. Não se presume cobertura completa desses ambientes pelo emulador.

## Próximo bloco proposto

Área de histórias e história do personagem, começando pelas aparições verificáveis. Inspecionar somente seus frames e o contrato real necessário; não incluir quadrinhos, filmes, séries e arcos numa única entrega. Aguardar validação deste perfil antes de iniciar.
