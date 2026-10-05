# Bloco 12 — Detalhes de séries e episódios

Histórias → Séries → Detalhes da série, no destaque e na seleção. Java/MVVM/XML, recursos visuais compartilhados com detalhes de filmes. Imagem, título, ano, editora, quantidade cadastrada, personagens Marvel com perfis nativos, descrição traduzida sob demanda e episódios em lotes de 12. Ausências ocultam seções; falhas permitem repetir sem perder os itens já carregados.

## Contrato primário

[Execução 37290599976](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37290599976), artefato 11335634703. `series/4075-ID/`: valida ID, caminho, editora Marvel 31 e caminho publisher/4010-31. Campos diretos: image, name, start_year, count_of_episodes, deck, description, characters, episodes, first_episode, last_episode. Nomes próprios e títulos originais preservados. HTML é convertido em texto seguro antes de tradução pelo serviço existente com cache persistente; original é conservado no modelo/resposta.

`episodes` fornece referências com ID/caminho/nome/episode_number. Ordenação derivada da numeração integral (numérica quando possível, desempate por ID), sem inferir temporadas. `episodes/` aceita filtro por IDs múltiplos e fornece air_date e series. Uma consulta por lote de 12, sem detalhe por item: valida todos os IDs/caminhos e vínculo recíproco com a série, antes de exibir. Datas ausentes ou inválidas são ocultas; datas válidas têm rótulo de exibição informada. A lista é parcial enquanto houver referências a carregar.

`first_episode`/`last_episode` são indicações da fonte, exigem presença na lista validada. Não significam sequência cronológica calculada nem fim definitivo. A sondagem mostrou inconsistências: Agatha possui 9 referências e último indicado 108. Não se corrige essa informação por suposição. Filtro de séries em episódios foi observado, mas a implementação usa o índice de referências e lote de IDs para não admitir episódios estranhos.

Personagens são cruzados com índice canônico Marvel antes de carregamento progressivo. Sem relações de quadrinhos, adaptações ou episódios recentes presumidos; não há campo de temporada confirmado. Títulos licenciados continuam identificados como tais pelo critério de editora da fonte.

## Verificação

Compilação/lint/emulador pendentes nesta etapa; sem testes unitários adicionados. Roteiro: abrir destaque e seleção; conferir ano/quantidade; abrir personagem e voltar; carregar mais episódios; ler descrição; retornar ao catálogo; repetir offline após cache; conferir tela pequena/grande e fonte 200%.

## Próximo bloco proposto

Formulário guiado de criação de herói com Marv, seleção real de origens e poderes e revisão dos dados, antes da integração de geração paga.
