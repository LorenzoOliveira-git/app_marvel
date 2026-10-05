# Bloco 19 — Meus heróis e edição textual

Este bloco parte do PR [27](https://github.com/LorenzoOliveira-git/app_marvel/pull/27), ainda aberto ao iniciar o trabalho. A branch mantém essa dependência explícita; não integra nem publica os PRs automaticamente.

## Fluxo entregue

No perfil autenticado, `Meus heróis` abre a coleção privada da conta. A consulta usa Firestore `Source.SERVER`, com páginas de 20 registros, ordenação estável por criação/ID e `Carregar mais heróis`. Não usa rascunhos ou operações preparadas como personagens concluídos. Há estados reais de carregamento, vazio, indisponibilidade e falha de conexão, com atualização manual. Cada item identifica nome e identidade; selecionar abre os dados e tenta obter a imagem privada real.

O editor permite alterar nome do herói, identidade e descrição. Campos obrigatórios têm limites de 100/100/2.000 caracteres, conferidos no Android e no backend. Imagem, origem, poderes, nascimento, dados da tentativa e orçamento permanecem preservados. Não há chamada de geração ou provedor pago na função de edição.

A imagem segue o caminho privado do bloco 18: autorização pelo proprietário, URL temporária e download em memória. A URL não vai para `SavedStateHandle`, cache em disco ou logs. Falha oferece novo download sem gerar outra imagem.

`Salvar alterações` chama `updateHeroText` e só informa confirmação após reler o documento no servidor. Campos permanecem preenchidos em falhas. Voltar ou recarregar com alterações ainda não salvas abre confirmação de descarte. Fonte ampliada usa controles de altura variável e rolagem; Marv mantém a expressão existente.

## Concorrência e identidade

A callable exige ambiente emulado demo, autenticação e uma lista estrita de campos permitidos. O UID vem do token. Escrita direta de clientes continua proibida pelas regras. A transação busca o herói exclusivamente no caminho da conta; não cria documentos ausentes.

`textRevision` começa em zero para os registros existentes. A transação compara a revisão antes de atualizar somente os três textos, revisão e data de atualização. Uma edição concorrente retorna conflito sem sobrescrever o registro. Repetir exatamente os textos já presentes após perda de resposta não incrementa a revisão novamente. O cliente não repete gravações automaticamente.

O editor conserva campos e revisão em `SavedStateHandle`. Ao restaurar o processo, relê o herói no servidor; revisão divergente exige recarregar explicitamente antes de salvar. Recarga com campos alterados pede confirmação de descarte. Troca de conta invalida callbacks pendentes, limpa formulário, coleção e bitmap e consulta a nova conta. O listener de autenticação e o executor de imagem são liberados ao descartar o ViewModel.

## Validação e limites

O diagnóstico deste bloco reutiliza somente os helpers de UiAutomator e executa uma passagem dos quatro SDKs/emuladores. A nova integração confere callable sem autenticação, payloads inválidos, campos de imagem/UID rejeitados, herói ausente sem criação e escrita direta bloqueada. No Android registra conta real emulada, abre perfil/coleção vazia, interrompe efetivamente a conexão Firestore, verifica erro e recuperação por atualização, fonte 200% e retorno ao login após sair.

Na primeira execução, compilação/lint e os testes de backend passaram; o roteiro Android não localizou a tela de login antes de chegar à coleção. O início do diagnóstico foi ajustado para limpar a tarefa anterior, com captura da tela/log de erro se a falha voltar. Na execução seguinte, cadastro passou; o helper de toque bloqueou a aba Perfil por só permitir Criar herói na área do menu inferior. Essa restrição do diagnóstico foi corrigida sem alterar o aplicativo. A passagem posterior confirmou coleção vazia e falha de rede, mas a primeira atualização após restaurar a rede ainda encontrou o transporte offline. A ação manual após erro de rede passou a reiniciar a conexão Firestore antes de consultar o servidor, sem repetir gravações ou geração.

A execução final [37355150514](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37355150514) passou no código `b478d7a606b15acbcc261532e137c4ee63cefb36`: compilação/lint, quatro SDKs emulados, controles reais do backend, coleção vazia, falha de rede, atualização com conexão reaberta, fonte 200% e saída da conta. As capturas de vazio com fonte ampliada e erro foram inspecionadas uma vez; textos e botões permanecem legíveis e alcançáveis.

Não cria personagem/imagem de exemplo para preencher a coleção. Não há mocks ou testes unitários. Coleção preenchida, paginação com mais de 20 heróis, edição bem-sucedida/conflito sobre um herói concluído e download de sua imagem real continuam pendentes de validação externa. Os provedores OpenAI/Cloudinary não foram exercitados. Nenhum deploy ou APK de distribuição é publicado.

## Teste local

1. Use os quatro emuladores e o perfil `-PfirebaseEmulators=true` conforme [firebase-local.md](firebase-local.md).
2. Entre ou cadastre uma conta e abra Perfil → Meus heróis. Sem criação concluída, deve aparecer a coleção vazia real.
3. Para a primeira criação real, configure os provedores apenas no ambiente privado e siga [bloco-18-android-criacao.md](bloco-18-android-criacao.md). Nunca envie credenciais na conversa.
4. Após concluir e conferir o herói real, abra-o na coleção, edite os textos e confira o registro no servidor. A imagem/referência deve permanecer idêntica. Uma segunda sessão com revisão antiga deve receber conflito ao salvar textos diferentes.

## Próximo bloco proposto

Completar os detalhes do perfil e o acesso ao herói concluído a partir da tela de criação. A primeira validação real com os provedores privados continua pendente e deve ser registrada quando houver configuração, sem substituir esse teste por dados fictícios.
