# Bloco 13 — Criação guiada com Marv e revisão do rascunho

O destino Criar agora oferece três etapas nativas em Java/MVVM/XML/ViewBinding: identidade; categoria de origem, poderes e descrição; revisão editável. Nome do herói, nome real, origem, descrição e pelo menos um poder são obrigatórios. Nascimento pertence ao personagem e é opcional, escolhido em calendário e removível. A revisão precede qualquer geração.

## Fonte e mapeamento

O [contrato primário 37298139994](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37298139994), artefato `comicvine-hero-form-contract`, retornou 10 origens e 128 poderes em 05/10/2026. Origens e poderes são catálogos auxiliares gerais da ComicVine, não entidades atribuídas exclusivamente à Marvel. Não se inventa associação de editora nem relação com personagens oficiais. Os totais exibidos são obtidos da resposta e podem mudar.

| Dado | Fonte e tratamento | Validação / ausência |
|---|---|---|
| Nome do herói e nome real | Entrada do usuário | Ambos obrigatórios; espaços isolados não contam |
| Nascimento | Calendário Android; ISO `yyyy-MM-dd`, apresentação `dd/MM/yyyy` | Opcional e removível; ausência aparece como não informado |
| Origem | `origins/`: id, name | Categoria, não local de nascimento; escolha obrigatória |
| Poderes | `powers/`: id, name, api_detail_url | Caminho HTTPS canônico `power/4035-ID/`, IDs positivos/únicos, pelo menos uma seleção |
| Rótulos de origem | Serviço ML Kit e cache existente | ID e nome original preservados; falha permite repetir |
| Rótulos de poder | `CatalogDescriptions.translatePower`, contexto e cache existentes | Fonte original separada do texto traduzido; sem poder fabricado |
| Descrição | Entrada do usuário, exibida como texto | Obrigatória; não passa por tradutor nem interpretação de HTML |
| Contador e paginação | number_of_page_results, number_of_total_results, offset | Lotes de 20 em ordem de nome da fonte; traduzir antes de incorporar |
| Revisão | Campos e escolhas do rascunho | Editável; nenhuma imagem gerada, nenhum herói salvo |

Falhas de API ou tradução não limpam campos nem seleções. Ao carregar outro lote, os já carregados continuam selecionáveis; somente o botão de carregamento aguarda o lote atual. A falha de tradução repete o mesmo offset, sem incorporar um lote parcialmente traduzido. Há estados reais de carregamento, erro com recuperação e vazio. Seleções de poderes também podem ser removidas acima da lista.

## Arquitetura e design

`CreateHeroViewModel` guarda campos, etapa, origem e poderes escolhidos em `SavedStateHandle`, usando valores Android suportados e separando IDs, nomes de fonte e rótulos. A tela observa estados e encaminha interações. `MarvelRepository.powers` reutiliza transporte e cache do catálogo. Não há chave, chamada de geração ou novo fornecedor de tradução no formulário.

O rascunho é de sessão: preservado ao navegar entre destinos e em recriações de configuração, com restauração de estado pelo Android quando disponível. Não é gravado em Firestore nem representa um herói criado. Encerrar a tarefa, limpar dados ou sair da conta pode descartá-lo. Visitantes podem preparar os dados; geração e salvamento com titularidade ficam para o próximo bloco.

Não havia frame específico de criação no mapeamento Figma aprovado. A composição reutiliza os inputs de autenticação, fontes e tema existentes, com alturas flexíveis, rolagem e insets de teclado/menu. Marv usa `marv_welcome` na identidade/revisão e `marv_thinking` na escolha de atributos. Orientações ficam em texto acessível fora das imagens; não há progresso fictício. Referência em `src/theme/create-hero.json`.

## Verificação

[Validação final 37301334928](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37301334928), commit `4940b8e7c2be0d87bac52eacb4d6125b2ecceb8b`: compilação, lint, ML Kit/cache e fluxo ADB do formulário aprovados. Lint: zero erros e 36 avisos preexistentes, sem novos avisos. Nenhum teste unitário adicionado; nenhum APK publicado automaticamente.

Integração trouxe 10 origens e dois lotes de 20 poderes, com IDs únicos e total real de 128. Dados e rótulos foram idênticos após reiniciar o processo sem rede. No fluxo nativo, o roteiro conferiu campos obrigatórios, seleção real de origem ID 3 e poder ID 66, carregamento de 20 → 40 preservando seleção, descrição, revisão, retorno para edição, navegação para Início e restauração, recriação com fonte 200% e tela 320×640. Dados digitados no roteiro são apenas entradas fictícias de um usuário de teste; catálogos e traduções são reais.

Sete capturas do formulário foram inspecionadas em uma conferência visual final. Textos e ações continuam alcançáveis por rolagem; nenhuma chamada paga ocorreu. Os fluxos já aprovados de quadrinhos, arcos, filmes e séries foram excluídos desta execução pelo escopo, mantendo compilação/lint e a integração compartilhada de tradução.

Evidências da execução: `android-catalog-check` (11341469618), `android-data-check` (11341434568), `android-preview` (11341304981) e `android-lint` (11341394788). Os artefatos permanecem disponíveis por sete dias; os contratos, roteiro e resultados resumidos estão preservados nesta documentação.

## Checklist e roteiro manual

- [x] Três etapas com orientação de Marv e retorno para editar.
- [x] Campos obrigatórios e nascimento opcional do personagem.
- [x] Origem categórica real, seleção múltipla de poderes e IDs preservados.
- [x] Carregamento progressivo, tradução com cache e recuperação sem apagar o rascunho.
- [x] Revisão distingue rascunho de geração e herói salvo.
- [x] Inputs, alvos de toque e rolagem usam componentes existentes.
- [x] Compilação/lint e integração real aprovados.
- [x] Revisão, edição, paginação, cache offline e restauração conferidos no emulador.
- [ ] Verificação manual adicional do calendário, remoção de data e TalkBack em aparelho físico.

1. Abrir Criar e tentar continuar sem preencher: conferir erros de nome do herói e nome real.
2. Preencher identidade. Escolher/remover uma data opcional e continuar.
3. Tentar revisar sem atributos: conferir origem, poder e descrição obrigatórios.
4. Escolher origem real, marcar vários poderes, remover um e carregar outros lotes. As escolhas restantes devem permanecer.
5. Preencher descrição e revisar. Conferir todos os campos, voltar para editar e revisar novamente.
6. Ir para Início e voltar para Criar. Mudar orientação, tamanho de tela e fonte: conferir campos e escolhas preservados, controles alcançáveis pela rolagem e menu/teclado sem cobrir ações.
7. Após carregar opções, desconectar a rede e conferir cache. Em instalação sem cache/modelo ou sob falha de fonte, conferir mensagem em português, recuperação e retenção dos campos.
8. Conferir que não aparece um herói salvo nem se inicia geração paga.

## Limites e próximo bloco proposto

A etapa entregue prepara e revisa dados. Geração da imagem, confirmação final para gastar crédito, autenticação exigida para salvar, Firestore/Cloudinary e apresentação do herói criado dependem da integração seguinte. Configuração de backend e provedor de geração deverá ser definida antes de qualquer uso pago. Não há chatbot, envio externo dos dados digitados, imagem substituída automaticamente nem item simulado na coleção.

Por solicitação do usuário, esta entrega usa uma revisão/validação principal. Outra passagem ocorre somente se surgir falha relevante; fluxos já aprovados não são repetidos para ajustes cosméticos.
