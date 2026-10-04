# Correções — dados locais e cabeçalho da Home

## Por que o Android Studio podia abrir sem dados

O transporte ComicVine lê `no_backup/comicvine-api-key` na instalação do aparelho. O Secret do GitHub era provisionado apenas no emulador do CI. O Run padrão do Android Studio não executava `tools/comicvine.py device`, e o aplicativo não lê o `.env` do computador. Por isso, uma instalação local sem provisionamento retornava `CREDENTIAL_REQUIRED`, apresentada como falha comum de carregamento.

Foi acrescentado `:app:runLocalDebug`: compila, instala no aparelho escolhido, lê o `.env` da raiz (ou variável de ambiente), transfere a chave por stdin para o armazenamento privado e abre o app. Não requer Python, ADB no PATH, backend, mocks ou chave em BuildConfig/assets. ADB é localizado no SDK do Android Studio. `:app:configureComicVineDebug` faz apenas o provisionamento de uma instalação debug existente. Sem aparelho único, exige seleção explícita com `-PcomicvineDevice=SERIAL`. A tarefa não participa de assemble/release automaticamente.

## Rodar na sua máquina

1. Atualize o código com estas correções e sincronize o Gradle no Android Studio.
2. Crie o `.env` **na raiz, ao lado de `settings.gradle.kts`**, com `COMICVINE_API_KEY=sua_chave_real`. Ele continua ignorado pelo Git. O Secret do GitHub não substitui esse arquivo na sua máquina.
3. Inicie um emulador ou conecte um aparelho autorizado. Deixe apenas um conectado, ou passe o serial na tarefa.
4. Execute a configuração **Sua Marvel (local)** no seletor do Android Studio. Alternativa no terminal PowerShell da raiz: `.\gradlew.bat :app:runLocalDebug`.
5. Abra “Explorar sem entrar”. Home e Personagens consultam a ComicVine real. A tradução inicial requer Wi-Fi para baixar o modelo ML Kit; capas e navegação não dependem dessa tradução.
6. Depois do primeiro provisionamento, pode usar o Run/Debug normal. Se desinstalar, limpar dados ou trocar de emulador/aparelho, execute a tarefa novamente. Para atualizar uma chave em instalação existente: `.\gradlew.bat :app:configureComicVineDebug`.

No Logcat debug, o filtro `ComicVine` informa somente categoria de falha e status HTTP (sem chave, URL, corpo ou e-mail). Isso permite distinguir credencial ausente, rede e limite da API. Nenhum aviso de configuração foi adicionado às telas do produto.

## Design

- Home: o cabeçalho “Seu universo / INÍCIO” foi substituído por avatar circular, nome e e-mail da sessão Firebase. Foto HTTPS existente na sessão é carregada com limite/cache; sem foto, usa ícone neutro. Visitante mostra “Visitante”, sem inventar nome, foto ou e-mail. Marca SUA/MARVEL, painel escuro até o fundo e navegação sobreposta mantidos.
- Estados comuns: carregamento usa indicador de progresso; seleção vazia usa ícone de busca; erro usa ícone de alerta e retentativa. Marv foi retirado desses estados compartilhados.
- Marv permanece em momentos de orientação e na curiosidade da Home, somente quando o conteúdo correspondente está carregado. Referências/recursos existentes reutilizados, sem novas chamadas ao Figma.

## Verificação

Em andamento: compilação/lint, fluxo Gradle local usando `.env`, ausência da chave no APK, ComicVine real, cache offline e capturas da Home/catálogo. Identidade/foto de uma conta Firebase real e Windows/Android Studio local precisam de validação na máquina do usuário; o CI usa Linux com emulador Android.

Estas correções pertencem ao bloco Home/Personagens. Não iniciam o próximo bloco e não disponibilizam APK automaticamente.
