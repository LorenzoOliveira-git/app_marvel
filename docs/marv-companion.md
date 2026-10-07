# Marv como parceiro de criação

O formulário agora reúne o Marv e sua orientação em uma ficha de papel. O botão “Marv, me dê uma dica” alterna orientações locais sobre identidade, aniversário, origem, poderes e descrição. Os campos de texto também mostram a dica junto ao campo focado, para que continue acessível quando o cabeçalho sair da tela.

A criação reage aos estados existentes do ViewModel: pensamento ao preparar, gerar ou salvar; celebração após confirmação de salvamento; acolhimento em falhas. As mensagens específicas, confirmações e ações existentes continuam responsáveis por explicar o andamento e autorizar cada tentativa. Tocar em dicas nunca inicia geração nem faz chamadas a uma IA.

A Home usa o Marv atento junto à curiosidade. A coleção vazia usa a saudação junto ao convite para criar o primeiro herói. As fichas preenchidas não recebem mascotes adicionais.

## Assets e movimento

- `app/src/main/res/drawable-nodpi/marv_companion_atlas.webp`: atlas transparente com seis poses em três colunas e duas linhas.
- `app/src/main/java/com/example/app_marvel/ui/components/MarvCompanionView.java`: desenho das células, transição de opacidade, oscilação e inclinação suaves; celebração com pequenos saltos. A saudação termina em repouso.
- `app/src/main/res/values/marv_companion.xml`: dicas e mensagens em português.

São ilustrações 2D com volume e animações de transformação, não um modelo 3D articulado. A imagem é compartilhada em memória; nenhuma biblioteca nova foi adicionada. Os movimentos duram poucos segundos, param quando o componente sai da área visível ou a tela pausa, e respeitam a escala de duração de animação do Android. Desativar animações mantém a expressão e a mensagem estáticas. O desenho é decorativo para leitores de tela; os textos e o botão continuam acessíveis.

## Origem visual

Asset criado com a ferramenta integrada de geração de imagens a partir de `marv_welcome.png`. Prompt usado:

> Create one production animation sprite atlas PNG for Android, transparent background. Reference image is the identity of MARV: preserve exactly this white/black/red cute robot with red cape, crest and M chest, same polished volumetric illustration. A precise 3 columns by 2 rows equal-cell grid, each full-body character centered at identical scale, generous padding within cells, no overlap, no ground shadows, no labels or text except M emblem. Row 1 left: calm neutral idle friendly open eyes, arms down. Row 1 middle: cheerful welcome waving one hand. Row 1 right: attentive explaining pointing one finger upwards. Row 2 left: thinking hand at chin, curious eyes. Row 2 middle: celebrating both hands up happy eyes. Row 2 right: reassuring gentle empathetic expression, open hands. All six poses clearly distinct, same proportions and frontal viewpoint. This is a sprite atlas, precisely aligned equal cells for programmatic rendering.

## Entrega

Não foram executados testes, lint, compilação ou emulador, conforme solicitado. A avaliação no aparelho fica com o usuário. O commit usa `[skip ci]` para não iniciar os workflows de push/PR; os arquivos de workflow não foram alterados.
