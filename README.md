# App_Marvel

Aplicativo Android em **Java, XML, Fragments e MVVM** para explorar o universo Marvel e, em etapas posteriores, criar heróis personalizados com a ajuda do Marv.

## Bloco 2 — splash e autenticação

Login/cadastro com Firebase Authentication real, estados e sessão, além do painel contínuo com menu sobreposto. Os formulários abrem sem avisos de desenvolvimento; use Explorar sem entrar para avaliar o painel. O Firebase precisa ser configurado para as operações de conta real. [Entrega do bloco 2](docs/bloco-2.md) e [configuração Firebase](docs/firebase-auth.md).

## Bloco 1 — fundação

- Uma Activity com NavHost e cinco destinos: Início, Personagens, Histórias, Criar herói e Perfil.
- View Binding, ViewModels/LiveData e repositório local de disponibilidade.
- Tema escuro inspirado em Marvel - Mobile, com Bebas Neue e fontes estáticas derivadas de Inter.
- Cards, campos/botões estilizados e componente de feedback com o Marv.
- Home de recepção; destinos mantêm sua apresentação sem avisos de desenvolvimento.
- **Sem dados fictícios, autenticação simulada, chamadas à ComicVine ou geração paga de heróis.**

O bloco não implementa ainda a home de notícias, destaques e curiosidades do Figma. Essa área depende de dados reais e será construída no bloco de conteúdo.

## Abrir e executar

1. Clone o repositório e selecione a branch `codex/bloco-2-auth-marv`.
2. Abra a raiz no Android Studio.
3. Mantenha os SDKs existentes: minSdk 24, compileSdk/targetSdk 37. Instale a plataforma Android 37.0 (pacote `platforms;android-37.0`) e Build Tools 36.0.0.
4. Disponibilize um JDK 25, conforme gradle/gradle-daemon-jvm.properties, e sincronize o Gradle.
5. Execute a configuração app em um emulador ou aparelho com API 24 ou superior.

Para habilitar contas reais, siga docs/firebase-auth.md e forneça app/google-services.json. A APK sem configuração permite inspecionar a interface.

Para compilar e verificar lint:

```bash
bash gradlew :app:assembleDebug :app:lintDebug --console=plain
```

No Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
```

O workflow Android build e lint executa essas mesmas tarefas e publica APK/relatórios quando a execução for bem-sucedida. Não executa testes unitários.

## Documentação

- [Entrega, checklist e roteiro manual do bloco 2](docs/bloco-2.md)
- [Configuração Firebase](docs/firebase-auth.md)
- [Entrega do bloco 1](docs/bloco-1.md)
- [Referência visual e uso do Marv](src/theme/README.md)
- [Mapeamento de frames](src/theme/frames.json)
- [Valores observados e normalizados](src/theme/tokens.json)
- [Proveniência dos assets](src/theme/assets.json)
- [Prompts dos assets do Marv](src/theme/marv-prompts.md)
- Licenças das fontes em docs/licenses.

A compilação `assembleDebug` e o `lintDebug` do bloco 2 passaram no [GitHub Actions](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37163811030). O run disponibiliza o APK `app-marvel-debug`, os relatórios `android-lint` e capturas `android-preview` por sete dias. Lint: zero erros e 13 avisos (atualizações disponíveis e dois recursos originais sem uso).

A tentativa local foi bloqueada no download do Gradle por Network is unreachable. No CI, o aplicativo foi instalado e aberto em emulador Android 35, com capturas de Login, Cadastro, Início, rolagem e fonte 200% inspecionadas. Autenticação com conta real ainda depende da configuração Firebase. Execute as verificações restantes do roteiro manual antes de aprovar o bloco.
