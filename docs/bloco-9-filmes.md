# Bloco 9 — Catálogo de filmes

Rota: Histórias → Filmes Marvel. MVVM Java, Fragments, XML e ViewBinding; destaque Iron Man, carrossel de pôsteres, A–Z/Z–A, busca pelo título original, seleção persistida, paginação progressiva, estados Marv e cache local. O botão de fonte abre explicitamente a ComicVine; detalhes nativos ficam para o próximo bloco.

## Contrato real

As sondagens 37250280724 e 37250524288 confirmam que `/movies/` ignora `publisher`, `studio` e `studios` como filtros. A lista fornece `studios` com referências do tipo 4010. A entidade Marvel é resolvida e verificada pelo endpoint publisher. Cada filme só entra se o lote contém uma referência Marvel com ID, nome e URL 4010 correspondentes; `/studio/4010-31/` retorna 404 e não é consultado. Nenhuma hidratação por item. Registros sem vínculo explícito ficam de fora: catálogo parcial, incluindo produções animadas, curtas e registros anunciados quando possuem o vínculo.

Cada ação examina até três lotes de 100, devolvendo um cursor global absoluto. O botão carregar mais permanece disponível mesmo em lote sem filmes elegíveis. A busca `name` funciona sem diferenciar maiúsculas, é conferida localmente e rejeita delimitadores de filtro. Cache de respostas e identidade preservam páginas para uso offline.

`release_date` repete `date_last_updated` em todos os 100 registros da sondagem; não representa lançamento confiável. Não há ordenação cronológica, data, saga, volume ou arco inventado. Duração vem de `runtime` quando presente; nomes originais são preservados.

## Design

Frame Figma Filmes 62:480, contrato registrado em `src/theme/movies.json`. Componentes, tokens, fontes, fundo e navegação existentes foram reaproveitados. Imagens estáticas de Watchmen/Superman e metadados fictícios do frame foram substituídos por dados verificáveis. Controles mínimos de 48dp e destaque empilhado em largura pequena/fonte grande.

## Validação

Compilação e lint passaram (zero erros, 33 avisos). O run [37251257995](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37251257995) aprovou os fluxos anteriores e os dados/cache offline de filmes: primeiro lote com 7 filmes, próximo com 12, ordem descendente com 17 e busca Iron Man com 3. O roteiro visual parou num seletor ambíguo do diálogo de busca; a correção e a verificação isolada de filmes estão em andamento. Roteiro debug verifica destaque, páginas disjuntas, busca Iron Man, exclusão Batman, vazio, entradas inválidas e equivalência offline. Roteiro ADB verifica carrossel, navegação/restauração e capturas em 430×932, 320×640, 640×1000 e fonte 200%. Nenhum teste unitário; nenhum APK publicado automaticamente.

## Roteiro manual

1. Abrir como visitante e entrar em Histórias → Filmes Marvel. Conferir destaque Iron Man e pôsteres Marvel no carrossel.
2. Alternar A–Z/Z–A e buscar `Iron Man`: são exibidos os três registros com vínculo confirmado. Buscar `Batman` deve ficar vazio.
3. Usar anterior/próximo ou deslizar o carrossel. Voltar a Histórias e reabrir: busca, ordem e seleção devem permanecer.
4. Carregar mais; o contador pode crescer em quantidades diferentes porque a seleção Marvel é feita antes da exibição. Um lote sem resultados ainda permite avançar se existem registros globais restantes.
5. Abrir a fonte ComicVine pelo botão explicitamente externo. Sem aplicativo compatível, a interface informa que não foi possível abrir o link.
6. Após carregar as consultas online, desligar a rede e reabrir: páginas já visitadas continuam disponíveis. Consultas novas exigem rede.
7. Conferir largura 320dp, 640dp e fonte 200%, com rolagem e destaque empilhado. Imagens ausentes usam o componente existente, sem capa inventada.

## Próximo bloco proposto

Detalhes nativos de filme: verificar contratos de personagens, equipes, descrição e créditos; incluir apenas campos confiáveis e navegar para os perfis dos personagens. Datas continuam ocultas enquanto a inconsistência de `release_date` não for resolvida ou outra fonte oficial for autorizada.
