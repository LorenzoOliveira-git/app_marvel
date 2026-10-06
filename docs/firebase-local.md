# Firebase local — Bloco 14

Escopo: Authentication, Functions, Firestore e Storage nos emuladores locais. O perfil usa `demo-marvel-local`, sem projeto real, conta Firebase, cartão ou plano Blaze. Não há deploy. O backend de geração OpenAI/upload Cloudinary/persistência foi acrescentado no bloco 17, desativado por padrão e ainda sem validação externa; esses provedores não são simulados pelos emuladores.

## Configurar em até seis passos

1. Instale Node.js 22, JDK 25 (usado pelo projeto Android e compatível com os emuladores) e o Android SDK do projeto. Abra um emulador Android no Android Studio.
2. Na raiz do repositório, execute `npm ci --ignore-scripts` e `npm ci --prefix backend --ignore-scripts`.
3. Execute `npm run emulators` e mantenha esse terminal aberto. Não precisa fazer login na CLI nem fornecer `google-services.json`. O primeiro início baixa os emuladores.
4. Em outro terminal, execute `./gradlew :app:installDebug -PfirebaseEmulators=true` (Windows: `gradlew.bat`). Abra o aplicativo no emulador Android.
5. Cadastre uma conta e entre no app. As contas são exclusivamente locais. Confira dados no painel `http://127.0.0.1:4000`. O catálogo ComicVine conserva sua configuração independente já documentada; não é emulado pelo Firebase.
6. Para conferir backend e regras, pare o processo do passo 3 e execute `npm run check:local`. Ele inicia os quatro emuladores, cria contas de diagnóstico, verifica identidade e isolamento e encerra os processos. A validação Android automatizada adicional fica no workflow **Firebase local e Android**.

O perfil é habilitado explicitamente por `-PfirebaseEmulators=true` apenas no debug. Sem a propriedade, o app mantém sua configuração Firebase normal. A variante release conserva `FIREBASE_EMULATORS=false` mesmo quando a propriedade é passada. Para usar Run/Debug do Android Studio no perfil local, defina `firebaseEmulators=true` nas propriedades Gradle locais da sua máquina e remova ao voltar ao perfil normal.

| Serviço | Computador | Aplicativo no emulador Android |
|---|---|---|
| Authentication | 127.0.0.1:9099 | 10.0.2.2:9099 |
| Functions | 127.0.0.1:5001 | 10.0.2.2:5001 |
| Firestore | 127.0.0.1:8080 | 10.0.2.2:8080 |
| Storage | 127.0.0.1:9199 | 10.0.2.2:9199 |
| Painel | 127.0.0.1:4000 | Abra no navegador do computador |

`10.0.2.2` é o acesso ao computador a partir do emulador Android. Para celular físico, use o encaminhamento USB descrito abaixo. HTTP é permitido somente para os hosts locais especificados na variante debug. A instância Firebase local é nomeada `marvel-local`, separando a sessão da instância normal.

O comando `npm run emulators` restaura os dados de `firebase-local-data/` e exporta o estado ao encerrar normalmente com Ctrl+C. Na primeira execução, sem uma exportação anterior, começa vazio. A pasta já está ignorada pelo Git. Usuários reais, recursos de produção e credenciais administrativas não são usados. As contas e arquivos de diagnóstico são dados de teste nos serviços reais emulados, sem imagem ou herói fictício apresentado como criação concluída.

## O que foi implementado

- SDKs Android de Auth, Functions, Firestore e Storage com endpoints definidos antes do uso.
- Função `localSessionCheck`: exige sessão, deriva UID da identidade verificada, grava uma confirmação no Firestore e devolve o UID. Fora do emulador, recusa execução.
- Firestore: proprietário pode ler a confirmação; cliente não pode escrevê-la. Admin SDK da função grava somente para o UID autenticado.
- Storage: diagnóstico de texto privado por UID, até 1 KiB. Leitura e gravação exigem proprietário. Outros caminhos permanecem bloqueados.
- Diagnóstico Android debug verifica cadastro, saída/entrada, função autenticada, leitura no servidor Firestore, upload/download/exclusão Storage. Diagnóstico HTTP confere bloqueio entre contas, sem autenticação, escrita direta no Firestore, tamanho e tipo do arquivo.

Não executar deploy dessas regras esperando persistência de heróis: elas permitem somente os diagnósticos locais especificados. A função local é deliberadamente bloqueada em produção. O fluxo real de criação será implementado em outro bloco.

## Rascunhos do formulário

O bloco 15 conecta a revisão à função local `saveHeroDraft`. Para validar origens/poderes na fonte, a ComicVine precisa estar configurada no ambiente privado do backend. Veja [bloco-15-rascunho-local.md](bloco-15-rascunho-local.md). As regras agora também permitem leitura dos rascunhos pelo proprietário, com escrita exclusiva do backend. A persistência de rascunhos não equivale à conclusão de heróis.

O [bloco 16](bloco-16-preparacao-criacao.md) acrescenta `prepareHeroCreation`: versão imutável, prompt privado e operação sem duplicação, com a política visual e os limites já aprovados. A preparação não gera imagem nem reserva dinheiro/cotas. A execução dos provedores e o bloqueio financeiro são descritos no bloco 17.

O [bloco 17](bloco-17-execucao-local.md) implementa no backend a execução com reserva financeira/cotas, geração única e recuperação de envio/salvamento. A CI mantém a execução paga desativada e verifica controles nos emuladores. As integrações OpenAI/Cloudinary ainda precisam da configuração privada e da verificação externa; a execução está conectada ao formulário Android no [bloco 18](bloco-18-android-criacao.md).

O [bloco 18](bloco-18-android-criacao.md) conecta o formulário à preparação, à confirmação paga, aos estados reais e à recuperação da criação. A configuração externa continua privada e ausente neste workspace; o cenário automatizado usa o bloqueio real de geração e não cria imagens falsas. Firestore do perfil local passa a usar cache em memória.

## Migrar futuramente para produção

| Reaproveitar | Ajustar ou configurar |
|---|---|
| Código Java e padrões dos SDKs | Desativar perfil local e adicionar `google-services.json` do projeto real |
| Backend Node e validação de identidade | Implementar função de negócio, selecionar projeto/região e fazer deploy explícito |
| Estrutura de regras e isolamento por UID | Definir coleções/arquivos de negócio e publicar regras revisadas |
| Fluxo e validações funcionais | Habilitar serviços reais, permissões, faturamento e limites operacionais |
| Integrações externas quando implementadas | Configurar segredos no servidor e contas OpenAI/Cloudinary; elas não são emuladas |

Portanto, o código pode ser compartilhado, mas a configuração de conexão e operação não é idêntica. Passar no emulador não valida IAM, cotas, escalabilidade ou cobrança de produção. Functions em produção requer Blaze; o teste local não.

## Referências oficiais

- https://firebase.google.com/docs/emulator-suite/connect_and_prototype
- https://firebase.google.com/docs/emulator-suite/connect_auth
- https://firebase.google.com/docs/emulator-suite/connect_functions
- https://firebase.google.com/docs/emulator-suite/connect_firestore
- https://firebase.google.com/docs/emulator-suite/connect_storage

## Evidências de validação

[Execução dos SDKs e das regras nos emuladores](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37309726711). Os artefatos contêm `backend.json`, `android.json` e relatórios de lint. O workflow compila debug e release, confere que o perfil padrão e release não habilitam emuladores e executa os diagnósticos HTTP e Android. Não executa testes unitários nem publica APK automaticamente.

## Coleção e edição textual no Android

O [bloco 19](bloco-19-meus-herois.md) acrescenta Perfil → Meus heróis e a callable local `updateHeroText`. A coleção lê documentos confirmados no servidor e a edição altera somente nome, identidade e descrição. Revisão transacional protege contra sobrescrita de outra edição; imagem/referência e origem/poderes não são alterados. Sem heróis concluídos, a tela mostra o estado vazio real.

## Nome do perfil e acesso ao herói concluído

O [bloco 20](bloco-20-perfil-acesso.md) usa o Firebase Auth emulado para editar/reler o nome da conta. O nome dos heróis permanece independente. Após a criação real concluída, um atalho abre o documento proprietário na coleção, sem gerar novamente ou transportar URL assinada pela navegação.

## Diagnóstico dos provedores

Com os emuladores iniciados, `npm run providers:status` mostra a configuração carregada pelo backend. `npm run providers:status -- --connections` solicita consultas externas de acesso ao modelo e ping Cloudinary, sem gerar ou enviar imagem. Credenciais ficam somente em `backend/.env.local`; presença e consultas não comprovam a primeira criação real. Veja [bloco 21](bloco-21-provedores.md) para interpretar o relatório e os limites da validação.

## Celular físico por USB

Ative a depuração USB no celular, conecte-o ao computador e autorize esse computador na tela do aparelho. Mantenha `npm run emulators` rodando. No PowerShell, com um único aparelho conectado:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb reverse tcp:9099 tcp:9099
& $adb reverse tcp:5001 tcp:5001
& $adb reverse tcp:8080 tcp:8080
& $adb reverse tcp:9199 tcp:9199
.\gradlew.bat :app:installDebug -PfirebaseEmulators=true -PfirebaseEmulatorHost=127.0.0.1
```

Se o Android SDK estiver em outro caminho, ajuste `$adb`. Abra o app instalado diretamente no celular. As quatro portas no aparelho encaminham para os serviços no computador, sem expor os emuladores na rede Wi-Fi. Repita `adb reverse` após desconectar/reiniciar o aparelho ou o ADB. Com vários dispositivos, acrescente `-s SERIAL` ao ADB e selecione o celular para instalação.

Para usar Run/Debug do Android Studio, configure `firebaseEmulators=true` e `firebaseEmulatorHost=127.0.0.1` no seu arquivo Gradle local, sincronize e mantenha os encaminhamentos ativos. Para voltar ao emulador Android, use `firebaseEmulatorHost=10.0.2.2` (padrão). Release conserva os emuladores desativados. Contas do Auth emulado são separadas das contas de produção; cadastre uma conta local.

## Identificar falhas ao salvar rascunhos

O app distingue chamada Functions, validação ComicVine e confirmação Firestore. No debug, o Logcat `HeroDraftSave` registra somente `stage` e `code`, sem personagem, UID, chaves, token ou mensagem bruta. No celular por USB, Functions depende de reverse 5001 e a releitura confirmada de reverse 8080. O backend precisa de `COMICVINE_API_KEY` em `backend/.env.local`, independentemente da chave usada pelo catálogo Android no `.env` da raiz.

A alteração separa erros antes apresentados como falha genérica e mantém os campos/ID para uma nova tentativa explícita. A causa da falha específica no aparelho ainda precisa do novo aviso ou do código no Logcat; não foi reproduzida apenas com a mensagem antiga. A CI focal desta correção exercita o salvamento Android, leitura no servidor, interrupção real da porta Functions e nova tentativa usando loopback/ADB reverse.

## Preservar a conta e o herói entre sessões

`npm run emulators` usa `--import=./firebase-local-data` e `--export-on-exit=./firebase-local-data`. Auth, Firestore e Storage são exportados juntos: conta, rascunhos, heróis, referências da imagem, operações e contadores de tentativas permanecem na próxima sessão. Functions volta a carregar o código e `backend/.env.local`; o arquivo de ambiente não faz parte da exportação. A imagem concluída continua privada na Cloudinary, e o app pede outra URL temporária ao abrir o herói.

**Se você já criou um herói com o comando antigo, mantenha os emuladores atuais abertos.** Após atualizar o repositório, execute na raiz em um segundo terminal:

```powershell
npm run emulators:export
```

Espere a confirmação de exportação antes de parar o terminal antigo com Ctrl+C. Se já houver exportação nessa pasta, a CLI pergunta antes de sobrescrevê-la. Depois inicie `npm run emulators` novamente. Não execute `check:local` no lugar dessa sessão: os diagnósticos iniciam emuladores separados, sem importar nem exportar seus dados.

Nas próximas sessões, encerre com Ctrl+C uma vez e aguarde a exportação e o encerramento completo. Encerramento forçado ou queda do computador pode perder alterações desde a última exportação; `npm run emulators:export` também permite salvar manualmente durante a sessão. Não apague `firebase-local-data/` se quiser manter a conta e a coleção. O backup contém dados locais de autenticação e personagens e deve permanecer privado.

Para validar seu herói real sem consumir outra geração:

1. Mantenha os emuladores rodando, feche e reabra o app, entre na mesma conta e abra Perfil → Meus heróis.
2. Abra o herói e confira imagem, nome, identidade e descrição. O download usa uma URL privada nova; não gera outra imagem.
3. Edite um texto, salve e confirme o resultado ao sair e voltar à coleção. Essa ação não chama a Cloudflare.
4. Faça a exportação inicial acima, encerre e reinicie os emuladores. Reabra o app na mesma conta e confira novamente o herói e a imagem.

A geração real foi confirmada pelo usuário. Reabertura da coleção, edição e restauração desse herói no aparelho ainda precisam de validação local; o workspace não acessa o celular nem seus emuladores.

Referência: https://firebase.google.com/docs/emulator-suite/install_and_configure#export_and_import_emulator_data
