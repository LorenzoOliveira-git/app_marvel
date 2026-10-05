> Atualização: a integração de geração foi migrada para Cloudflare FLUX.2 klein 9B. Veja [cloudflare-flux.md](cloudflare-flux.md) para configuração atual; referências à OpenAI abaixo descrevem a implementação anterior.

# Bloco 21 — Diagnóstico dos provedores locais

Parte do `main` após o merge do PR 29 (`2a5d763`). Acrescenta a callable autenticada `providerConfigurationCheck` e o comando `providers:status`. O backend recusa o diagnóstico fora do projeto demo e de Auth emulado em loopback.

## Usar no computador

1. Instale as dependências e configure os quatro emuladores conforme [firebase-local.md](firebase-local.md). Copie `backend/.env.example` para `backend/.env.local` e preencha as credenciais somente nesse arquivo privado. Não envie valores ao chat, ao Git ou à APK. Mantenha `HERO_GENERATION_ENABLED=false` durante a configuração.
2. Execute `npm run emulators` e mantenha esse terminal aberto. Reinicie os emuladores após alterar o ambiente privado.
3. Em outro terminal, execute `npm run providers:status`. O comando verifica a configuração carregada pelo backend, sem consultar os provedores externos.
4. Opcionalmente, execute `npm run providers:status -- --connections` para consultar o acesso ao modelo e o ping administrativo Cloudinary. A opção não gera imagem nem faz upload. Essas consultas não provam autorização para gerar, enviar ou baixar imagens.

O comando cria uma conta temporária no Auth local, chama a callable com identidade verificada e exclui a conta ao terminar. O relatório usa uma lista explícita de campos; não imprime UID, token, senha, chaves, nome da nuvem ou resposta bruta dos provedores.

| Campo | Interpretação |
|---|---|
| `configured` | Credenciais presentes no ambiente; não comprova validade |
| `connection: not_checked` | Consulta externa não solicitada |
| `connection: not_configured` | Credenciais ausentes; nenhuma consulta feita |
| `connection: passed` | Consulta de modelo ou ping respondeu; geração/upload continuam pendentes |
| `connection: unavailable` | Consulta falhou; resposta privada não é exposta |
| `configurationComplete` | Sinalização de geração, credenciais e hosts dos emuladores presentes; não é prova de criação |
| `firstRealAttemptPending: true` | O diagnóstico nunca confirma uma imagem gerada |

Após verificar a configuração, habilite a geração deliberadamente no ambiente privado e use o fluxo Android com a confirmação de tentativa e os limites já aprovados. A primeira criação deve confirmar geração, upload, persistência do herói, abertura na coleção, edição e download privado. Esse teste real continua pendente: as credenciais externas não estão disponíveis neste ambiente.

## Validação

`npm run check:providers` inicia Auth e Functions e exercita a callable por HTTP: autenticação obrigatória, rejeição de campos desconhecidos, configuração ausente, consulta sem credenciais, saída restrita da CLI e exclusão das contas temporárias. Nenhum herói ou imagem fictícia é criado.

Neste ambiente, Functions Emulator falhou ao abrir seu socket Unix, tanto em `/tmp` quanto na pasta de trabalho. A alternativa `npm run check:providers:sdk` passou com Auth Emulator real e a callable real do Firebase Functions SDK servida por HTTP em loopback. A autenticação usa o SDK e tokens do emulador, sem identidade simulada. Essa alternativa não inicia o Functions Emulator, não carrega `.env.local` automaticamente e não substitui sua validação na CI. O teste passou em Node 24; o projeto e a CI mantêm Node 22.

O workflow **Firebase local e Android** inclui o diagnóstico no Functions Emulator nativo e a validação Android do perfil do bloco 20. A [execução inicial](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37369614209) permanecia na fila sem executor ao registrar esta entrega. Compilação, lint, validação Android e Functions Emulator nativo ainda estão pendentes; o PR permanece em rascunho até esses checks concluírem. Consultas externas com credenciais e a primeira criação real também continuam pendentes.
