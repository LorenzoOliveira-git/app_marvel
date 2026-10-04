# Referência visual — Marvel - Mobile

Arquivo: avJ7cq3FyO5XOIueiqaYrb. Esta pasta documenta o design; o Android usa app/src/main/res.
Frames analisados no diagnóstico: Login (30:296) e Tela Inicial (19:102).
A consulta get_variable_defs em Login retornou {}. Os valores observados foram extraídos do contexto dos frames; valores normalizados foram escolhidos para o aplicativo. Não representam uma biblioteca completa de variáveis do Figma.

O bloco 1 estabelece a identidade e a navegação. A home de recepção é provisória para a fundação: os cards de notícias, destaque e curiosidade serão implementados com dados reais no bloco de conteúdo. O fundo usa um gradiente Android em lugar dos brilhos decorativos; não é uma reprodução pixel a pixel. Os ícones e o logo foram exportados dos próprios nós, sem modificar o arquivo.

## Marv

- Recepção/home: pose frontal sorrindo.
- Avisos de indisponibilidade: pensativo.
- Componente de estado: preparado para carregamento, vazio e erro; não simula esses estados no bloco 1.
- Login/cadastro futuros: orientação curta, sem cobrir campos ou teclado.
- Conteúdo futuro: dicas contextualizadas e avisos de ausência; imagens de personagens continuam vindas da ComicVine.
- Criação futura: orientação de campos, revisão e etapas reais; expressões de sucesso/falha deverão corresponder ao resultado.
- Perfil futuro: avatar selecionável apenas entre imagens aprovadas; não há conta fictícia neste bloco.

Os dois PNGs foram preparados pelo ImageGen a partir do guia anexado. São assets derivados da referência, não recortes determinísticos. Não foi configurada geração paga de heróis.

## Bloco 2

Sem novas chamadas ao Figma. Login/Home reutilizados do cache. Painel até o rodapé, menu sobreposto e fonte de títulos explícita. Login/cadastro reutilizam Marv existente junto ao formulário/status. Cadastro adaptado dos componentes locais; splash nativa, sem cópia das três telas ainda não consultadas.

## Correção da marca e autenticação

Marca restaurada: SUA (Bebas Neue) centralizado acima do símbolo MARVEL, usando o asset original. No Login/Cadastro, o slot de 169×73dp preserva o conteúdo de aproximadamente 149×69dp da referência (o export inclui margem transparente); no cabeçalho principal, slot original de 76×33dp. Splash nativa compõe os contornos do mesmo símbolo em vetor Android com SUA derivado da fonte local, para preservar a marca dentro da máscara do sistema. O PNG original continua inalterado no cabeçalho. Login reutiliza título 40sp, rótulos 32sp, campos de borda branca e links 20sp. Nenhuma nova consulta Figma. Mensagens sobre etapas de implementação ficam na documentação, fora da UI.

## Bloco 3 — Home e personagens

Referências em cache de Home e Personagens reutilizadas. Carrossel com retrato central maior, seções de publicações/destaque/curiosidade, busca e filtros reais. Geometria e adaptações em characters-reference.json; mapeamento de dados/trechos factuais em docs/bloco-3-home-personagens.md. Marv aparece em estados operacionais e junto à curiosidade; retratos vêm exclusivamente da ComicVine.
