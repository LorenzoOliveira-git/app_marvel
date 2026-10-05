# Bloco 15 — Rascunho autenticado no Firebase local

## Entrega

No perfil de emuladores, a revisão do formulário permite **Salvar rascunho**. Visitantes são encaminhados ao Login; entrar ou cadastrar uma conta retorna à revisão com os campos e as seleções preservados. O usuário confirma o salvamento após entrar.

O ViewModel mantém o mesmo identificador UUID durante a sessão/restauração Android. O repositório envia o rascunho à função `saveHeroDraft` e confirma o documento no servidor Firestore antes de mostrar sucesso. Progresso e erros são reais; a falha mantém os dados e permite nova tentativa. Alterar os campos remove o estado de sucesso anterior.

A função exige identidade autenticada, deriva UID dessa identidade e aceita somente os campos de rascunho. Valida textos obrigatórios, nomes até 100 caracteres, descrição até 2.000, nascimento opcional e uma lista de 1–128 IDs de poderes, sem repetição. O limite de quantidade é operacional para o rascunho; não é uma cota de geração. Origem e poderes precisam existir no catálogo real da ComicVine, consultado pelo backend com credencial privada e cache de uma hora. Nomes canônicos são obtidos da fonte, não confiados ao aplicativo.

O documento `users/{uid}/heroDrafts/{draftId}` contém os campos, origem/poderes canônicos, `state: draft`, `generatedImage: false`, hash do conteúdo e datas do servidor. Salvar novamente o mesmo conteúdo não altera a data nem cria outro documento; editar atualiza o mesmo rascunho em transação. Leitura é exclusiva do proprietário e escrita/exclusão direta pelo cliente é bloqueada.

Este bloco salva um rascunho, sem gerar imagem, criar herói na coleção ou chamar OpenAI/Cloudinary. A função e o botão estão limitados ao ambiente local nesta etapa. Não há recuperação automática de rascunhos remotos entre instalações; os campos atuais usam o SavedStateHandle Android já existente. Ao encerrar os emuladores sem exportação, os dados do servidor local são perdidos.

## Configurar em até seis passos

1. Use a configuração de [firebase-local.md](firebase-local.md), com Node 22, JDK 25 e dependências instaladas por `npm ci` e `npm ci --prefix backend`.
2. Configure sua chave ComicVine existente no arquivo privado `backend/.env.local`, com a linha `COMICVINE_API_KEY=valor_da_sua_chave`. Não envie o valor ao chat nem ao GitHub. Esse arquivo é ignorado pelo Git. Alternativamente, forneça essa variável no ambiente do terminal que inicia os emuladores.
3. Execute `npm run emulators` na raiz. A primeira validação precisa de internet para consultar a ComicVine; sem chave/rede, o salvamento não é confirmado. Não requer projeto Firebase real ou faturamento.
4. Instale com `./gradlew :app:installDebug -PfirebaseEmulators=true` e configure a ComicVine no aparelho pelo procedimento já existente (`:app:configureComicVineDebug` com o `.env` local da raiz).
5. Abra Criar herói, preencha os dados, revise e toque em **Entrar para salvar rascunho**. Entre ou cadastre uma conta, confira os campos ao retornar e toque em **Salvar rascunho**.
6. Confira a mensagem e o documento no painel `http://127.0.0.1:4000`. Edite e salve novamente para conferir que o mesmo rascunho é atualizado. Para a integração automatizada de backend, use `firebase emulators:exec` com `node tools/check-hero-drafts-local.cjs`, no ambiente que tem a chave privada disponível.

O catálogo auxiliar de origens/poderes continua geral da ComicVine, sem alegação de associação exclusiva à Marvel. Nenhuma nova expressão do Marv, provedor ou credencial de imagem foi introduzida.

## Validação principal

Uma passagem no workflow **Firebase local e Android** compila debug e lint e usa Auth, Functions, Firestore e Storage reais emulados. O diagnóstico de backend confere campos inválidos, IDs inexistentes, tentativa de forjar proprietário, ausência de autenticação, leitura entre contas, escrita/exclusão direta, repetições concorrentes e edição com nascimento opcional.

No Android, o roteiro seleciona um lote de catálogos com traduções reais, abre o formulário, passa por cadastro/retorno à revisão, interrompe a conexão da função local, confirma erro com campos mantidos, restaura a conexão e salva. O documento é lido no servidor e os IDs comparados com a fonte. A interface é conferida com fonte 200%. Os dados de usuário são entradas sintéticas somente para diagnóstico; fontes de catálogo e serviços não são simulados.

Não há testes unitários, publicação automática de APK ou deploy.

## Próximo bloco proposto

Preparar a operação de criação a partir do rascunho validado. Antes de habilitar geração real, confirmar direção visual/proporção, teto e cotas, configurar as credenciais privadas e verificar o acesso ao modelo aprovado. O emulador Firebase não simula nem elimina a cobrança de OpenAI ou Cloudinary.

## Resultado

[Validação principal aprovada](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37316214179) no commit de código `c66d8a6540458606a0f4d52836d2820adb7a99c7`: compilação, lint e todas as verificações descritas passaram. Artefato `firebase-local-check` com relatórios JSON e uma captura de revisão/sucesso em fonte 200%. O erro anterior foi da leitura de XML do UiAutomator durante a abertura da tela; a correção ficou restrita ao roteiro de teste. A implementação do app/backend permaneceu a mesma.
