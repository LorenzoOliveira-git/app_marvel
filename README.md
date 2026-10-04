# App_Marvel

Aplicativo Android em **Java, XML, Fragments e MVVM** para explorar o universo Marvel e, em etapas posteriores, criar heróis personalizados com a ajuda do Marv.

## Bloco 2 — splash e autenticação

Login/cadastro com Firebase Authentication real, estados e sessão, além do painel contínuo com menu sobreposto. Os formulários abrem sem avisos de desenvolvimento; use Explorar sem entrar para avaliar o painel. O Firebase precisa ser configurado para as operações de conta real. [Entrega do bloco 2](docs/bloco-2.md) e [configuração Firebase](docs/firebase-auth.md).

## Bloco 3 — Home e personagens

HQs recém-publicadas, Spider-Man em destaque, resumo/curiosidade traduzidos e catálogo com carrossel, busca, filtros e paginação. Relações Marvel verificadas antes da exibição; respostas, imagens e traduções têm cache. [Entrega e mapeamento](docs/bloco-3-home-personagens.md).

Configure a chave ComicVine fora do APK: `.env` local na raiz → `python tools/comicvine.py device` com instalação debug conectada via ADB. O Secret do GitHub configura somente o emulador de CI. [Configuração detalhada](docs/bloco-3-proposta.md).

## Abrir e executar

1. Clone o repositório e selecione a branch `codex/bloco-3-home-personagens` (ou `main` após incorporar o PR).
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

- [Entrega e mapeamento do bloco 3](docs/bloco-3-home-personagens.md)
- [Entrega, checklist e roteiro manual do bloco 2](docs/bloco-2.md)
- [Configuração Firebase](docs/firebase-auth.md)
- [Entrega do bloco 1](docs/bloco-1.md)
- [Referência visual e uso do Marv](src/theme/README.md)
- [Mapeamento de frames](src/theme/frames.json)
- [Valores observados e normalizados](src/theme/tokens.json)
- [Proveniência dos assets](src/theme/assets.json)
- [Prompts dos assets do Marv](src/theme/marv-prompts.md)
- Licenças das fontes em docs/licenses.

A compilação `assembleDebug` e o `lintDebug` do bloco 2 passaram no [GitHub Actions](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37166914245). O run disponibiliza o APK `app-marvel-debug`, os relatórios `android-lint` e capturas `android-preview` por sete dias. Lint: zero erros e 14 avisos (atualizações disponíveis, dois recursos originais sem uso e uma recomendação genérica para dimensão de vetor na splash).

A tentativa local foi bloqueada no download do Gradle por Network is unreachable. No CI, o aplicativo foi instalado e aberto em emulador Android 35, com capturas da splash, Login, Cadastro, cinco destinos do menu, validação, rolagem e fonte 200% inspecionadas. Autenticação com conta real ainda depende da configuração Firebase. Execute as verificações restantes do roteiro manual antes de aprovar o bloco.
