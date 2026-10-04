# App_Marvel

Aplicativo Android em **Java, XML, Fragments e MVVM** para explorar o universo Marvel e, em etapas posteriores, criar heróis personalizados com a ajuda do Marv.

## Bloco 2 — splash e autenticação

Login/cadastro com Firebase Authentication real, estados e sessão, além do painel contínuo com menu sobreposto. Os formulários abrem sem avisos de desenvolvimento; use Explorar sem entrar para avaliar o painel. O Firebase precisa ser configurado para as operações de conta real. [Entrega do bloco 2](docs/bloco-2.md) e [configuração Firebase](docs/firebase-auth.md).

## Bloco 3 — Home e personagens

HQs recém-publicadas, Spider-Man em destaque, resumo/curiosidade traduzidos e catálogo com carrossel, busca, filtros e paginação. Relações Marvel verificadas antes da exibição; respostas, imagens e traduções têm cache. [Entrega e mapeamento](docs/bloco-3-home-personagens.md).

Perfil nativo acessível pela Home e pelo catálogo: identidade, descrição traduzida, primeira aparição, poderes e carrosséis paginados de equipes/aliados/inimigos. [Detalhes de personagem](docs/bloco-3-detalhes-personagem.md).

Configure `COMICVINE_API_KEY` no `.env` local da raiz e execute **Sua Marvel (local)** no Android Studio, com um emulador/aparelho iniciado. Alternativa no PowerShell: `.\gradlew.bat :app:runLocalDebug`. A tarefa instala, configura a chave privada no aparelho e abre o app; depois pode usar Run/Debug normalmente. O Secret do GitHub configura somente o emulador de CI. [Configuração local e correções de design](docs/correcoes-dados-home.md).

## Bloco 4 — Histórias e aparições

Histórias com destaque real, primeira aparição e edições vinculadas ao personagem, paginadas e verificadas por volumes Marvel. Acessível também por **Ver aparições** nos perfis. Datas da capa e sequência do catálogo identificadas; resumo/origem traduzidos e cache persistente. [Entrega e roteiro](docs/bloco-4-historias-aparicoes.md).

## Bloco 5 — Quadrinhos

Catálogo nativo acessível por Histórias, com destaque, capas em carrossel, busca de volumes Marvel, Recentes/Antigos e paginação. Edições verificadas pelo índice de volumes, dados em cache e links ComicVine explícitos. [Entrega e roteiro](docs/bloco-5-quadrinhos.md).

## Abrir e executar

1. Clone o repositório e selecione a branch `codex/bloco-5-quadrinhos` (ou `main` após incorporar o PR).
2. Abra a raiz no Android Studio.
3. Mantenha os SDKs existentes: minSdk 24, compileSdk/targetSdk 37. Instale a plataforma Android 37.0 (pacote `platforms;android-37.0`) e Build Tools 36.0.0.
4. Disponibilize um JDK 25, conforme gradle/gradle-daemon-jvm.properties, e sincronize o Gradle.
5. Inicie um emulador ou aparelho com API 24 ou superior e execute **Sua Marvel (local)** com o `.env` configurado.

Para habilitar contas reais, siga docs/firebase-auth.md e forneça app/google-services.json. A APK sem configuração permite inspecionar a interface.

Para compilar e verificar lint:

```bash
bash gradlew :app:assembleDebug :app:lintDebug --console=plain
```

No Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
```

O workflow Android build e lint executa essas tarefas e verifica o app no emulador. Publica relatórios; APK somente em execução manual. Não executa testes unitários.

## Documentação

- [Quadrinhos: entrega e roteiro](docs/bloco-5-quadrinhos.md)

- [Histórias e aparições: entrega e roteiro](docs/bloco-4-historias-aparicoes.md)
- [Detalhes de personagem: entrega e roteiro](docs/bloco-3-detalhes-personagem.md)

- [Correções de dados locais e design da Home](docs/correcoes-dados-home.md)
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
