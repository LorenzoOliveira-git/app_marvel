# Bloco 16 — Preparação da criação no backend local

O usuário aprovou em 05/10/2026 a direção de HQ colorida, corpo inteiro, retrato 2:3, `gpt-image-2` com `low`, uma imagem, orçamento OpenAI de US$ 5/mês, 3 tentativas por usuário/dia, 100 tentativas totais/mês e nenhuma repetição automática de geração. Essa confirmação não precisa ser solicitada novamente. Firebase continua exclusivamente nos emuladores.

## Entrega

`prepareHeroCreation` é uma callable autenticada e limitada ao emulador. Recebe apenas `draftId`, `operationId` (UUID v4) e `contentHash` da versão revisada. O proprietário vem de `request.auth.uid`; campos de modelo, custo, imagem, estado e proprietário fornecidos pelo cliente são rejeitados.

O backend lê o rascunho salvo, verifica sua identidade, normaliza novamente os campos e confere o hash. Origem e poderes são revalidados no catálogo ComicVine real. Uma transação grava:

| Documento | Conteúdo e acesso |
|---|---|
| `users/{uid}/heroCreationJobs/{operationId}` | Cópia imutável do personagem e da política aprovada; leitura somente pelo dono, escrita somente pelo backend |
| `users/{uid}/heroCreationInputs/{operationId}` | Prompt de estrutura fixa e parâmetros de imagem; acesso exclusivo do backend |
| `users/{uid}/heroCreationClaims/{draftId}` | Operação atual do rascunho; acesso exclusivo do backend |

Pedidos simultâneos para o mesmo rascunho e hash retornam a mesma operação, mesmo com UUIDs diferentes. Repetição não altera a data nem o prompt. O mesmo UUID não pode ser reaproveitado para outro conteúdo. Uma nova versão do rascunho substitui a operação anterior somente enquanto ela estiver `prepared`; a anterior passa a `superseded`, preservando seu conteúdo. Uma versão já em execução deverá ser resolvida antes de preparar outra.

O prompt usa os campos como um objeto JSON, estabelece estilo/formato no servidor e inclui aniversário apenas quando preenchido. Os dados do usuário não controlam parâmetros ou ferramentas. A delimitação reduz a mistura entre instruções e dados; não garante perfeita fidelidade visual de um modelo generativo.

## Limite desta etapa

`prepared` significa dados preparados, sem imagem, sem geração iniciada e sem herói concluído. Não há reservas financeiras nem consumo de tentativas. Os valores aprovados estão versionados no backend, mas o bloqueio transacional de orçamento/cotas ainda será implementado na execução paga. Nenhum limite financeiro operacional pode ser declarado ativo nesta etapa.

Não há cliente OpenAI ou Cloudinary, fila de execução, upload, arquivo temporário ou botão de preparação no Android neste bloco. A revisão continua salvando rascunhos pelo fluxo validado no bloco 15. Não foram configuradas ou solicitadas credenciais externas, realizado deploy, publicada APK ou executada geração paga.

## Verificação

O workflow `hero-preparation-local.yml` executa integração com Authentication, Functions, Firestore e Storage reais emulados, além da ComicVine real. Confere os rascunhos e regras anteriores, pedidos concorrentes, rejeição de versão desatualizada/reutilização de identidade, imutabilidade após edição, estado substituído, política aprovada e acesso exclusivo ao prompt. Não usa mocks nem testes unitários. As evidências são JSON sem tokens, senhas ou chaves.

A validação Android extensa não se repete nesta branch: não houve alteração no aplicativo. A compilação/lint e o formulário continuam cobertos pela execução aprovada do bloco 15. A sintaxe Node e a integração específica cobrem as alterações deste bloco.

## Como verificar localmente

1. Use a configuração local já descrita em [firebase-local.md](firebase-local.md), com Java 21 ou superior e Node 22. Instale dependências com `npm ci --ignore-scripts` e `npm ci --prefix backend --ignore-scripts`.
2. Mantenha `COMICVINE_API_KEY` somente em `backend/.env.local`, ignorado pelo Git. Não coloque chaves no aplicativo ou na conversa.
3. Execute os emuladores com `npm run emulators`. Para o diagnóstico automatizado, disponibilize a mesma chave ComicVine no ambiente privado do terminal e execute `npx firebase emulators:exec --project demo-marvel-local --only auth,firestore,functions,storage "node tools/check-hero-preparation-local.cjs"`, com os emuladores anteriores encerrados.

## Próximo bloco proposto

Executar a operação preparada no backend local com reserva transacional de orçamento/cotas, chamada única à OpenAI, armazenamento temporário privado, upload autenticado no Cloudinary e gravação do herói. Retomadas de upload/gravação reutilizam a imagem persistida; falha ou resultado desconhecido da geração não dispara outra chamada automaticamente. Uma operação substituída não pode gerar.

Configurar as credenciais privadas dos provedores e verificar o acesso real da conta ao modelo aprovado serão necessários para a validação externa. Os emuladores não substituem nem simulam esses provedores. Depois, conectar o Android à confirmação explícita e aos estados reais. Produção exige configuração própria e permanece fora deste piloto.

Referência de parâmetros consultada: [OpenAI Images API](https://developers.openai.com/api/reference/resources/images/methods/generate). Nenhuma verificação de acesso da conta ou estimativa nova de preço foi obtida nesta etapa.
