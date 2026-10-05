# Bloco 18 — Criação conectada ao Android

O PR 26 foi integrado, merge `49806256882faa63a4632ffd4c9f2ecf61c076fe`. Este bloco conecta o formulário ao backend local de preparação/execução/recuperação. Direção visual e limites já foram aprovados; a confirmação na tela autoriza uma tentativa específica, não pede novamente aprovação da arquitetura.

## Fluxo entregue

1. Na revisão, `Gerar imagem do herói` exige uma conta autenticada. Login/cadastro conserva o formulário e retorna à revisão, como no bloco 15.
2. O aplicativo salva o rascunho no servidor, lê seu hash e chama `prepareHeroCreation`. Apenas após confirmar a operação no servidor abre o diálogo de tentativa paga.
3. O diálogo descreve HQ colorida/corpo inteiro/retrato 2:3, reserva conservadora de US$ 0,05, limite mensal de US$ 5, 3 tentativas por conta/dia e 100 totais/mês. Informa que a reserva não é o custo real, falhas contam e não existe regeneração automática.
4. Cancelar conserva a operação preparada sem executar geração. Confirmar chama `executeHeroCreation` uma vez para aquela identidade; o backend conserva a deduplicação transacional.
5. A tela observa etapas reais do servidor, sem percentuais: geração, armazenamento, envio, gravação, falha, resultado desconhecido e conclusão. Dados de uma tentativa iniciada ficam bloqueados para edição.
6. Após `completed`, o aplicativo confirma o documento do herói no servidor e pede uma URL Cloudinary temporária mediante verificação do proprietário. A foto é baixada em memória; falha de imagem oferece novo download, sem chamar geração. `Criar outro herói` abre um formulário novo; edição da imagem existente não está disponível.

Salvar rascunho continua sendo uma ação separada, agora visualmente secundária. Nenhuma preparação, recuperação de tela ou consulta de estado dispara automaticamente a operação paga. Dados da confirmação permanecem no backend; modelo, qualidade, tamanho, quantidade, UID, custos e referência da imagem não são determinados pelo cliente.

## Recuperação e identidade

`Verificar e retomar criação` é separado de `Solicitar nova geração paga`. Na etapa preparada, a verificação apenas consulta o servidor. Nas etapas em andamento/falha de envio/resultado desconhecido, solicita a retomada sem regeneração. Outra tentativa de geração exige um novo diálogo e UUID estável próprio. Retomar durante o lease ativo apenas consulta o andamento; o backend só assume recuperação depois do lease, conforme o bloco 17.

O ViewModel conserva campos e operação em `SavedStateHandle`. Após encerramento do processo, um formulário vazio consulta a operação mais recente da conta no servidor, restaura sua cópia de dados e traduz novamente origem/poderes com o ML Kit já existente. Se o usuário já está preenchendo um novo formulário, essa consulta não sobrescreve seus campos. Uma falha de tradução/restauração permite verificação/retomada novamente; não usa rótulos falsos ou imagem de exemplo.

O aplicativo aceita confirmação somente de leituras `Source.SERVER` ou eventos do listener com `isFromCache=false`. Cache/desconexão pode indicar que o estado não foi atualizado; nunca produz novo sucesso local. UID e identidade de operação são verificados nas respostas e nos documentos. Funções de execução/retomada têm timeout do cliente de 550 segundos para o timeout de backend de 540; nenhuma repetição automática foi acrescentada.

Listeners são removidos ao descartar o ViewModel. Troca de conta invalida callbacks antigos, apaga estado privado da criação, formulário vinculado e bitmap; uma resposta de outra sessão não reaparece na tela. No perfil local, Firestore usa cache em memória para não gravar novas operações privadas em cache persistente. Nenhuma URL assinada ou foto do herói é guardada em disco. Catálogos/imagens públicos ComicVine continuam usando seus mecanismos anteriores.

As URLs de imagem são aceitas somente em HTTPS no endpoint de download `api.cloudinary.com`, sem redirecionamento ou token Firebase anexado. O download respeita limite de 16 MiB e PNG com as dimensões esperadas; reduz a resolução em memória para apresentação. URLs vencidas são obtidas novamente após autorização. A tela limpa a foto quando a sessão/operação muda.

## Escopo e validação

O perfil exige `-PfirebaseEmulators=true`; não há ativação automática no perfil normal/release ou deploy em produção. Não foram adicionadas dependências, alterado SDK mínimo/target ou substituídos Java/MVVM/XML/ViewBinding. Marv usa expressões existentes; não se criou chatbot, imagem de exemplo ou porcentagem fictícia.

O workflow focal compila debug local e lint e executa o fluxo nativo com Auth, Functions, Firestore e Storage emulados. Reaproveita uma passagem dos diagnósticos anteriores: catálogos/traduções reais, registro/retorno, falha efetiva de conexão e salvamento confirmado. O novo roteiro verifica preparação, diálogo/cancelamento, execução desativada retornando falha real, preservação do UUID/campos, encerramento/reabertura do processo, recuperação da operação do servidor e fonte 200%. Uma leitura administrativa confirma que não houve reserva financeira nem herói criado no cenário desativado. Sem mocks, imagens falsas ou testes unitários.

A primeira execução de CI passou ([37339898537](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37339898537), código `c1a7dd2dd86f205f9d2462ee2df593e49b9eaab5`). A inspeção visual identificou o cancelamento parcialmente cortado no diálogo com fonte 200%; o rótulo positivo foi encurtado para `Gerar`, preservando o título e o consentimento explícito. O roteiro passou a exercitar cancelamento também nessa escala. A execução seguinte não localizou a tela recuperada após reabrir o processo; o diagnóstico foi ajustado para aguardar a tela inicial autenticada antes de abrir o formulário e registrar uma captura em caso de falha.

A execução final passou ([37342611797](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37342611797), código `d56d6d7b24a161d7dc2af82e6dd476603188c217`): build/lint, quatro SDKs emulados, regras, preparação/cancelamento, bloqueio real, preservação da identidade/campos e recuperação após encerramento do processo. A captura final de fonte 200% foi inspecionada: os dois botões permanecem visíveis; o roteiro também executou cancelamento nessa escala. Nenhum documento de herói nem reserva financeira foi criado no cenário desativado.

Limitação: OpenAI/Cloudinary ainda não têm credenciais configuradas neste workspace. Não foram validados geração paga, upload real, conclusão com imagem real ou download da imagem real no Android. O código desses caminhos está conectado, mas não é apresentado como sucesso já observado. A CI força geração desativada. Nenhuma APK foi publicada e nenhum deploy foi executado.

## Para testar localmente — quatro passos

1. Configure os emuladores e ComicVine conforme [firebase-local.md](firebase-local.md), com Java/Node compatíveis. Para testar provedores reais, preencha as credenciais apenas em `backend/.env.local` conforme [bloco-17-execucao-local.md](bloco-17-execucao-local.md); não envie valores na conversa.
2. Inicie `npm run emulators`. Mantenha `HERO_GENERATION_ENABLED=false` para verificar o bloqueio sem gastar; habilite explicitamente no arquivo privado e reinicie somente quando quiser testar geração real.
3. Compile/instale o perfil local: `bash gradlew :app:runLocalDebug -PfirebaseEmulators=true`. A chave ComicVine no `.env` da raiz é provisionada no aparelho pelo mecanismo existente, nunca no APK.
4. Crie uma conta no emulador, preencha/revise e toque em gerar. Cancelamento deve conservar a preparação; no cenário desativado, confirmação mostra indisponibilidade sem reserva. Com provedores configurados, uma tentativa paga ainda deverá conferir herói/imagem reais. Em falha de envio, use retomada; não solicite outra geração para recuperar o mesmo arquivo.

## Próximo bloco proposto

Coleção dos heróis personalizados da conta e edição textual mantendo a imagem fixa, com estados vazio/carregando/erro reais. A validação externa de geração/upload/download permanece necessária quando as credenciais privadas estiverem configuradas; não exige nova aprovação de estilo/orçamento já registrados.

Referências: [Callable Android](https://firebase.google.com/docs/reference/android/com/google/firebase/functions/HttpsCallableReference), [listeners Firestore](https://firebase.google.com/docs/firestore/query-data/listen), [MemoryCacheSettings](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/MemoryCacheSettings).
