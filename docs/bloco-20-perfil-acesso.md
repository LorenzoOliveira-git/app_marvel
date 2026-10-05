# Bloco 20 — Nome do perfil e acesso ao herói concluído

Parte do PR [28](https://github.com/LorenzoOliveira-git/app_marvel/pull/28), ainda aberto ao iniciar o bloco. Mantém a dependência entre branches, sem merge automático, deploy ou distribuição de APK.

## Entrega

O perfil autenticado oferece `Editar nome da conta`. O formulário conserva o campo ao girar/recriar a tela, exige nome não vazio com até 100 caracteres e bloqueia ações durante o envio. Nome da conta e nomes dos heróis são independentes. E-mail, senha e foto não são alterados nesta ação.

O repositório usa a sessão atual do Firebase Auth, atualiza `displayName` e chama `reload`. Só publica a confirmação quando a releitura devolve o nome pedido para o mesmo UID. Falha de escrita ou releitura conserva o campo, informa que não foi possível confirmar e permite uma nova ação explícita; não há repetição automática. Uma gravação cuja resposta/releitura falhou pode já ter sido aplicada no servidor; reenviar o mesmo nome não cria outro registro ou herói.

Cancelar ou voltar com o campo alterado pede confirmação de descarte. Ações de salvamento não guardam senha ou token no ViewModel. O campo é vinculado ao UID em `SavedStateHandle`; troca de conta limpa edição/estado e invalida callbacks anteriores. A observação da sessão é removida ao descartar o ViewModel. O cabeçalho e o perfil recebem o nome confirmado pela sessão.

Na revisão da criação, `Abrir herói na coleção` só aparece depois do estado `completed` e da confirmação do documento do herói no servidor. A navegação passa somente o UUID da operação, sem URL assinada ou dados da imagem. A coleção consulta esse documento no caminho da conta atual com `Source.SERVER`, confirma proprietário/identidade e abre o editor existente. O acesso aguarda a inicialização da sessão antes de consultar.

Se o registro estiver indisponível, não mostra dados de exemplo: oferece verificação e retorno à coleção. Uma tentativa inicial de acesso pode recuperar o documento posteriormente sem confundir ausência de revisão com conflito. Rotação conserva a edição já aberta; reentrada/recuperação usa os controles de revisão do bloco 19. UID diferente nunca consulta o caminho da conta anterior. A imagem mantém autorização e download em memória do fluxo existente; o atalho não chama geração.

## Validação e limites

O workflow focal compila debug local e lint e executa uma passagem pelos quatro SDKs/emuladores. O roteiro registra uma conta real emulada e verifica o nome pelo SDK e por uma leitura independente do Auth REST, sem imprimir credenciais. Exercita salvamento, manutenção do e-mail, cancelamento sem gravação, campo vazio rejeitado, interrupção real da rede Auth, preservação dos campos, nova ação de salvamento, fonte 200%, coleção vazia real e saída da conta.

As verificações locais de sintaxe Python, XML e `git diff --check` passaram. A execução [37364335395](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37364335395), código `9d6cfb59d0ef905111d923ddd7bab79fb77a72ab`, permaneceu na fila do GitHub sem executor durante esta etapa. Compilação/lint e integração Android ainda não foram executados para este bloco; o PR fica em rascunho até concluir essa validação e inspecionar as capturas.

Não cria herói ou imagem de exemplo. A abertura de um herói concluído pelo novo botão ainda depende da primeira geração real com os provedores privados. Coleção preenchida, edição textual/conflito sobre esse herói e download da imagem real continuam pendentes conforme o bloco 19. OpenAI/Cloudinary não são chamados pelo diagnóstico deste bloco. Não foram adicionadas dependências ou alterados SDK mínimo/target, Java, MVVM, XML ou ViewBinding.

## Conferência local

1. Use os quatro emuladores e o perfil `-PfirebaseEmulators=true` conforme [firebase-local.md](firebase-local.md).
2. Cadastre/entre na conta e abra Perfil → Editar nome da conta. Salve e confira o nome no perfil e no cabeçalho da tela inicial.
3. Edite novamente e cancele: o nome confirmado deve permanecer. Durante falha de rede, o campo deve ser mantido; só o envio explícito tenta salvar outra vez.
4. Após concluir a primeira criação real conforme [bloco-18-android-criacao.md](bloco-18-android-criacao.md), use `Abrir herói na coleção`. Confira UUID/proprietário, campos atuais e imagem privada. Não use um rascunho como substituto de um herói concluído.

## Próximo bloco proposto

Diagnóstico local da configuração dos provedores e preparação do primeiro teste real de criação. As credenciais permanecem no ambiente privado; a confirmação por tentativa e os limites já aprovados continuam sendo usados.

## Atualização após o merge

O PR 29 foi incorporado ao `main` em `2a5d763`. A execução 37364335395 foi cancelada sem executar etapas; não valida compilação/lint ou o perfil Android. O workflow do bloco 21 inclui novamente essa validação, ainda pendente enquanto aguarda executor.

A CI [37387716573](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37387716573) compilou e passou no lint/diagnóstico dos SDKs, salvamento e cancelamento do nome, mas parou na entrada vazia do roteiro: o campo ainda continha texto autocorrigido. O script agora seleciona todo o conteúdo e verifica o valor exato antes de continuar. Rede/retomada e fonte ampliada ainda precisam da passagem completa corrigida.
