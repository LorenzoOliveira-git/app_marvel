# Bloco 17 — Execução de geração no backend local

O bloco 16 foi integrado pelo PR 25, merge `2036e0978d26c51fcfdfaf27c1c349794670d91a`. Estilo, proporção, modelo e limites já estão aprovados; não pedir essa aprovação novamente. Esta entrega acrescenta execução e recuperação no backend. A aplicação Android ainda salva rascunhos; sua conexão à preparação/execução e à apresentação do herói será o próximo bloco.

## Operações

| Callable | Comportamento |
|---|---|
| `executeHeroCreation` | Exige operação preparada e `confirmPaidGeneration: true`; configuração privada e consulta de acesso ao modelo; reserva transacional; uma requisição de geração; armazenamento privado; upload; gravação |
| `resumeHeroCreation` | Retoma somente armazenamento/upload/gravação; nunca chama OpenAI nem cria outra reserva |
| `retryHeroGeneration` | Após falha/resultado desconhecido/imagem expirada, exige outro UUID e `confirmNewPaidAttempt: true`; prepara outra tentativa, que também exige confirmação na execução |
| `heroImageUrl` | Verifica o proprietário do herói e devolve download Cloudinary assinado, válido por cinco minutos |

Todas exigem sessão Firebase Auth verdadeira. Payloads não aceitam UID, referência da imagem, custo, modelo ou estado fornecidos pelo cliente. A execução exige `FUNCTIONS_EMULATOR=true`, projeto `demo-marvel-local` e endereços locais dos três emuladores administrativos. Falha fechada em produção. Nenhuma implantação ou fila Cloud Tasks de produção foi configurada. No piloto local, o trabalho ocorre dentro da callable (timeout 540s); a observação futura de estados no Android será separada da espera dessa chamada.

## Limites e dinheiro

A reserva lê a operação, o rascunho vigente, o vínculo de versão, o prompt privado, a política aprovada, o dia do proprietário e o mês global. A mesma transação registra a etapa `generating`, uma reserva e uma tentativa. Pedidos concorrentes recebem um único vencedor; repetição de operação já iniciada devolve seu estado.

Limites: 3 tentativas por usuário/dia, 100 globais/mês e US$ 5/mês de reservas, com dia/mês em `America/Sao_Paulo`. Valores monetários são inteiros em milionésimos de dólar. Cada tentativa reserva conservadoramente US$ 0,05. Falhas e resultados desconhecidos mantêm a reserva e a tentativa; não há liberação automática. Mesmo após sucesso, a reserva permanece: não é chamada de custo observado ou saldo reembolsável. O campo financeiro fica `reserved_pending_reconciliation`; contagens reais de tokens/request ID são registradas quando devolvidas pela API.

O prompt é limitado a 12.000 bytes UTF-8; modelo/tamanho/qualidade/quantidade são fixos. A reserva é um bloqueio operacional conservador, não uma garantia sobre a fatura externa, preços futuros, outros clientes que usem a mesma chave ou custos de Firebase/Cloudinary. O projeto OpenAI deve ser dedicado ao piloto e monitorado. Sem conciliação de cobrança nesta etapa, o backend prefere bloquear novas tentativas a presumir economia.

Os registros `heroGenerationUsage` e `heroGenerationDays` são privados do backend; não podem ser lidos ou alterados pelo aplicativo. A configuração `HERO_GENERATION_ENABLED` é `false` no exemplo e na CI. Credenciais ausentes/desativação não consomem cota nem iniciam chamada à API. A consulta de modelo antes da reserva verifica sua listagem para aquela chave; a permissão de gerar e o faturamento só ficam comprovados pela primeira geração real, ainda pendente.

## Imagem e recuperação

Uma única requisição HTTPS usa `gpt-image-2`, `quality: low`, `size: 1024x1536`, `n: 1`, `output_format: png`. Não há SDK com repetição automática para geração. Erros de transporte/5xx/resposta inválida ficam `generation_unknown`; uma rejeição HTTP 4xx fica `generation_failed`. Nunca devolver mensagens brutas do provedor, chaves ou imagem base64 ao aplicativo.

A imagem recebida é conferida como PNG 1024×1536 e gravada em `heroPending/{uid}/{operationId}.png` no Storage emulado privado, com hash, identidade, versão e expiração de sete dias. O cliente não tem leitura/escrita desse caminho; não é emitido token público de download. Uma queda após essa gravação permite recuperar a mesma imagem. Queda antes dela permanece desconhecida e requer nova tentativa explícita, sem repetir automaticamente a requisição.

Upload Cloudinary via SDK Node 2.11.0, assinado no backend, tipo `authenticated`, `overwrite: false`, public ID determinístico por proprietário/operação. Após timeout ou queda, consulta-se o mesmo ID para recuperar um upload já aceito. Gravação do herói e conclusão da operação compartilham transação; a identidade do herói é a da operação. O herói guarda a referência do ativo, nunca URL pública permanente, chave ou segredo. Após conclusão, o temporário é removido; falha de limpeza não reverte o herói nem gera outra imagem.

Etapas registradas: `prepared`, `generating`, `storing`, `image_stored`, `uploading`, `saving`, `completed`, `generation_failed`, `generation_unknown`, `upload_failed`, `save_failed`, `image_expired` e `superseded`. Não há porcentagem de progresso fictícia. Um lease de dez minutos impede duas retomadas simultâneas ou retomada durante a execução de até nove minutos. Se o processo morrer, a retomada fica disponível após o lease; jamais despacha outra geração.

O temporário expirado é recusado e removido durante sua recuperação. Para remover expirados nunca retomados, existe `tools/cleanup-hero-temporary-local.cjs`. Execute periodicamente no terminal local com `FIREBASE_STORAGE_EMULATOR_HOST=127.0.0.1:9199`, enquanto o emulador estiver rodando. O script só remove arquivos com identidade/expiração válidas sob `heroPending`; não altera Cloudinary, heróis ou reservas. Não há agendador permanente no piloto emulado.

## Configuração privada — até seis passos

1. Prepare um projeto dedicado da OpenAI com faturamento e acesso a `gpt-image-2`, e um ambiente Cloudinary. A assinatura do ChatGPT não configura a API. Não envie credenciais na conversa.
2. Copie `backend/.env.example` para `backend/.env.local` e preencha ComicVine, OpenAI e Cloudinary somente nesse arquivo local ignorado pelo Git. Não coloque valores no APK, `google-services.json` ou scripts versionados.
3. Instale as dependências (`npm ci --ignore-scripts` e `npm ci --prefix backend --ignore-scripts`); use Node 22 e Java 21 ou superior para os emuladores.
4. Para preparar o ambiente de geração real, mude `HERO_GENERATION_ENABLED=true` no arquivo privado e inicie/reinicie `npm run emulators`. Isso habilita a execução, mas não gera automaticamente. Produção continua bloqueada.
5. Após a conexão Android do próximo bloco, revise o personagem e confirme uma tentativa paga. A primeira geração real deverá conferir resposta OpenAI, imagem temporária, ativo autenticado Cloudinary e herói no Firestore. Essa verificação externa não foi executada neste workspace, pois as credenciais estão ausentes.
6. Em falha de envio/salvamento, use a retomada da mesma operação. Em falha/resultado desconhecido de geração, primeiro tente recuperar a imagem após o lease; outra geração exige nova identidade e confirmação explícita. Mantenha os emuladores/dados para recuperação; exporte se precisar persistir entre reinícios conforme o guia local.

## Validação desta entrega

Execução aprovada: [workflow 37334831954](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37334831954), commit `7593b04789cb9c2bc36a1e456af578f16e35ae1e`. Sintaxe Node, controles anteriores e novo diagnóstico passaram na primeira execução.

O diagnóstico novo utiliza Auth, Functions, Firestore e Storage reais emulados e catálogos reais ComicVine. Exercita concorrência de reserva, limite diário, último espaço mensal, orçamento independente da quantidade, configuração paga desativada, isolamento financeiro/temporário, recuperação de operação interrompida sem regeneração, confirmação/idempotência de nova tentativa e limpeza de expirados. Os valores nas bordas do orçamento são configurados explicitamente via Admin SDK no emulador; não são respostas de API simuladas.

Não usa mocks, imagens geradas falsas ou testes unitários. O diagnóstico reserva operações sem despachar provedores: isso valida controle transacional, não a integração paga. OpenAI, Cloudinary, geração real, upload real e salvamento de um herói com imagem real permanecem sem validação externa até a configuração privada. Nenhuma imagem ou sucesso de criação foi apresentado como real. Nenhuma APK foi publicada. A aplicação Android não mudou, portanto sua compilação/lint extensa não foi repetida.

Fontes consultadas em 05/10/2026: [OpenAI Images API](https://developers.openai.com/api/reference/resources/images/methods/generate), [preços OpenAI](https://developers.openai.com/api/docs/pricing), [upload Cloudinary](https://cloudinary.com/documentation/image_upload_api_reference), [SDK Node Cloudinary](https://cloudinary.com/documentation/node_integration), [Admin Storage Firebase](https://firebase.google.com/docs/storage/admin/start). Não houve verificação de acesso da conta do usuário.
