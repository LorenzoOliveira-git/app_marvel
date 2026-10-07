# Marv como parceiro de criação

O formulário agora reúne o Marv e sua orientação em uma ficha de papel. O botão “Marv, me dê uma dica” alterna orientações locais sobre identidade, aniversário, origem, poderes e descrição. Os campos de texto também mostram a dica junto ao campo focado, para que continue acessível quando o cabeçalho sair da tela.

A criação reage aos estados existentes do ViewModel: pensamento ao preparar, gerar ou salvar; celebração após confirmação de salvamento; acolhimento em falhas. As mensagens específicas, confirmações e ações existentes continuam responsáveis por explicar o andamento e autorizar cada tentativa. Tocar em dicas nunca inicia geração nem faz chamadas a uma IA.

A Home usa o Marv atento junto à curiosidade. A coleção vazia usa a saudação junto ao convite para criar o primeiro herói. As fichas preenchidas não recebem mascotes adicionais.

## Assets e movimento

- `app/src/main/res/drawable-nodpi/marv_welcome.png` e `marv_thinking.png`: ilustrações já presentes no projeto, usadas conforme o contexto.
- `app/src/main/java/com/example/app_marvel/ui/components/MarvCompanionView.java`: transição de opacidade, oscilação e inclinação suaves; celebração com pequenos saltos. A saudação termina em repouso.
- `app/src/main/res/values/marv_companion.xml`: dicas e mensagens em português.

São duas ilustrações 2D com volume e movimentos curtos, não um modelo 3D articulado nem seis artes distintas. As imagens são compartilhadas em memória; nenhuma biblioteca nova foi adicionada. Os movimentos duram poucos segundos, param quando o componente sai da área visível ou a tela pausa, e respeitam a escala de duração de animação do Android. Desativar animações mantém a expressão e a mensagem estáticas. O desenho é decorativo para leitores de tela; os textos e o botão continuam acessíveis.

## Entrega

Não foram executados testes, lint, compilação ou emulador, conforme solicitado. A avaliação no aparelho fica com o usuário. O commit usa `[skip ci]` para não iniciar os workflows de push/PR; os arquivos de workflow não foram alterados.
