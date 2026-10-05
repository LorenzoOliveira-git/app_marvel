# Bloco 9 — Catálogo de filmes

Rota: Histórias → Filmes Marvel. MVVM Java, Fragments, XML e ViewBinding; destaque Iron Man, carrossel de pôsteres, A–Z/Z–A, busca pelo título original, seleção persistida, paginação progressiva, estados Marv e cache local. O botão de fonte abre explicitamente a ComicVine; detalhes nativos ficam para o próximo bloco.

## Contrato real

As sondagens 37250280724 e 37250524288 confirmam que `/movies/` ignora `publisher`, `studio` e `studios` como filtros. A lista fornece `studios` com referências do tipo 4010. A entidade Marvel é resolvida e verificada pelo endpoint publisher. Cada filme só entra se o lote contém uma referência Marvel com ID, nome e URL 4010 correspondentes; `/studio/4010-31/` retorna 404 e não é consultado. Nenhuma hidratação por item. Registros sem vínculo explícito ficam de fora: catálogo parcial, incluindo produções animadas quando possuem o vínculo.

Cada ação examina até três lotes de 100, devolvendo um cursor global absoluto. O botão carregar mais permanece disponível mesmo em lote sem filmes elegíveis. A busca `name` funciona sem diferenciar maiúsculas, é conferida localmente e rejeita delimitadores de filtro. Cache de respostas e identidade preservam páginas para uso offline.

`release_date` repete `date_last_updated` em todos os 100 registros da sondagem; não representa lançamento confiável. Não há ordenação cronológica, data, saga, volume ou arco inventado. Duração vem de `runtime` quando presente; nomes originais são preservados.

## Design

Frame Figma Filmes 62:480, contrato registrado em `src/theme/movies.json`. Componentes, tokens, fontes, fundo e navegação existentes foram reaproveitados. Imagens estáticas de Watchmen/Superman e metadados fictícios do frame foram substituídos por dados verificáveis. Controles mínimos de 48dp e destaque empilhado em largura pequena/fonte grande.

## Validação

Compilação, lint e integração no emulador pendentes. Roteiro debug verifica destaque, páginas disjuntas, busca Iron Man, exclusão Batman, vazio, entradas inválidas e equivalência offline. Roteiro ADB verifica carrossel, navegação/restauração e capturas em 430×932, 320×640, 640×1000 e fonte 200%. Nenhum teste unitário; nenhum APK publicado automaticamente.
