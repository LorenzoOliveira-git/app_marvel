# Configurar Firebase Authentication — bloco 2

A integração E-mail/Senha está implementada. Esta entrega não recebeu configuração de um projeto Firebase; a APK sem configuração permite avaliar os formulários e explorar. Não há avisos de configuração na tela; uma tentativa válida de envio sem serviço configurado resulta em erro operacional, sem simular sucesso.

## Android Studio

1. Abra https://console.firebase.google.com/ e crie ou selecione o projeto que será usado pelo aplicativo.
2. Registre um aplicativo Android com o nome de pacote exato `com.example.app_marvel` (applicationId existente). Não altere o pacote do projeto para fazer a configuração corresponder.
3. Baixe o `google-services.json` e coloque-o em `app/google-services.json`. O arquivo contém identificadores públicos do aplicativo/projeto; não é uma chave administrativa. A integração não precisa de chave de conta de serviço.
4. No console, entre em Authentication > Get started > Sign-in method e habilite **E-mail/Senha**. Não é necessário habilitar link por e-mail para este fluxo.
5. Sincronize o Gradle e compile novamente. O plugin Google Services só é aplicado quando o arquivo existe. Firebase Auth usa os recursos gerados pelo plugin para inicializar a instância real.
6. Use uma conta de teste sua para validar cadastro e login. Confira o usuário em Authentication > Users. Não há usuários de exemplo embutidos na APK.

A validação local usa nome/e-mail/senha/confirmar senha obrigatórios e mínimo de seis caracteres no cadastro. Políticas mais fortes configuradas no Firebase são aplicadas pelo servidor e podem rejeitar uma senha que passou pelo mínimo local. Senhas não são aparadas. Entrar só exige uma senha não vazia, para respeitar contas existentes.

## Build da APK no GitHub

O arquivo é ignorado pelo Git. Para o workflow gerar uma APK configurada, crie em Settings > Secrets and variables > Actions um repository secret chamado `GOOGLE_SERVICES_JSON`, com **todo o conteúdo JSON**, sem codificação em base64. O workflow grava esse valor no arquivo antes de compilar, sem imprimi-lo.

Sem esse secret, o build de inspeção também compila e disponibiliza as telas. Adicionar o arquivo depois da compilação não configura uma APK já gerada: é necessário gerar outra.

## O que este bloco armazena

- Firebase Authentication cria o usuário, autentica e conserva a sessão do SDK.
- Nome visível usa `FirebaseUser.updateProfile`; e-mail vem do usuário autenticado.
- Se salvar o nome falhar após criar a conta, a conta já existe. O app informa isso e permite entrar; não tenta criar a conta novamente.
- Nenhuma senha/token é copiado para SharedPreferences, SavedStateHandle, arquivos de log ou banco do aplicativo. A senha fica em memória durante a edição/rotação e precisa ser digitada novamente após a morte do processo.
- Não há Firestore, coleção de heróis, avatar selecionável, troca/exclusão de conta ou recuperação de senha neste bloco.

## Referências consultadas

- https://firebase.google.com/docs/android/setup — BoM 34.19.0 e plugin Google Services 4.5.0.
- https://firebase.google.com/docs/auth/android/password-auth — criação, login, política de senha e saída.
- https://firebase.google.com/docs/auth/android/manage-users — perfil do usuário.

Não foi criado, configurado ou acessado um projeto Firebase pelo agente; falta a configuração fornecida pelo proprietário para verificar as operações reais.
