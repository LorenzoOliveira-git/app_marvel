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

`10.0.2.2` é o acesso ao computador a partir do emulador Android. Esta configuração não atende um celular físico diretamente. HTTP é permitido somente para esse host na variante debug. A instância Firebase local é nomeada `marvel-local`, separando a sessão da instância normal.

Os dados não são exportados automaticamente; ao encerrar os emuladores, o estado local é perdido. Usuários reais, recursos de produção e credenciais administrativas não são usados. As contas e arquivos de diagnóstico são dados de teste nos serviços reais emulados, sem imagem ou herói fictício apresentado como criação concluída.

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
