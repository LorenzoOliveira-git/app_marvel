# Bloco 14 — Proposta de geração e persistência

Status: proposta concreta, aguardando confirmação das decisões de backend, custos e direção visual. O bloco 13 foi integrado no PR 22, commit de merge `2dcaffbbbd5f372c52f0708c3b68a88433189df9`. Nenhuma integração paga ou configuração de serviço foi ativada nesta proposta.

## Decisões propostas

| Item | Proposta para aprovação |
|---|---|
| Backend | Cloud Functions for Firebase, com fila Cloud Tasks para trabalho assíncrono e Secret Manager |
| Dados e identidade | Firebase Authentication + Cloud Firestore, conforme escolha existente |
| Imagem final | Cloudinary, upload assinado no backend, entrega protegida |
| Recuperação | Bucket privado Cloud Storage apenas para imagem pendente, antes do upload final; apagar após conclusão e aplicar expiração de sete dias |
| Modelo e qualidade | OpenAI Images API, `gpt-image-2`, `quality: low`, uma imagem por chamada; sem substituição de modelo |
| Direção visual | Ilustração de HQ colorida, personagem original de corpo inteiro, fundo discreto, sem texto/logotipos |
| Proporção | Retrato 2:3, `size: 1024x1536` |
| Orçamento inicial OpenAI | US$ 5 por mês, com bloqueio operacional no backend baseado em reservas e consumo registrado |
| Limites de piloto | Até 3 tentativas por usuário por dia e 100 tentativas no total por mês; falhas/resultado incerto também consomem uma tentativa |
| Retentativas de geração | Somente explícitas e após nova confirmação do usuário; nenhuma repetição automática da chamada à OpenAI |
| Retentativas de upload/gravação | Retomar a mesma operação e reutilizar a imagem já persistida; sem nova geração |
| Depois de salvo | Imagem fixa; alterações textuais futuras não acionam geração. Exclusão continua fora do escopo |

O bucket temporário é uma dependência adicional proposta para recuperação confiável, não uma troca do Cloudinary como destino final. Cloud Functions exige projeto Firebase no plano Blaze, com cobrança por uso. Os custos de Firebase/Google Cloud e Cloudinary são separados do orçamento proposto para OpenAI; precisam de contas e alertas próprios. Esta proposta não promete um teto financeiro conjunto nesses fornecedores.

## Estimativa consultada em 05/10/2026

A documentação oficial lista `gpt-image-2` com `low`, `medium` e `high`. Para qualidade low, a referência de saída é US$ 0,005 em 1024×1536 e US$ 0,006 em 1024×1024. Acrescenta-se o texto do prompt. A conta precisa de acesso à API; o nível gratuito não suporta esse modelo. O acesso específico da sua conta ainda não foi verificado.

Preços de texto consultados: US$ 2,50 por milhão de tokens de entrada. Exemplo de estimativa, não medição: 1.000 tokens de prompt + imagem retrato low ≈ US$ 0,0075 por chamada; 100 chamadas ≈ US$ 0,75, excluindo infraestrutura, impostos, câmbio e novas tentativas. A cobrança real depende do uso devolvido pelo serviço e dos preços vigentes; não usar `auto` para tamanho/qualidade.

A implementação proposta reservará orçamento transacionalmente antes de chamar a API, limitará os campos e registrará custos/uso. Uma tentativa com resultado desconhecido mantém a reserva até conciliação. Alertas do console não substituem bloqueio no backend. O valor reservado por chamada será definido conservadoramente a partir da configuração e limites aprovados, e não confundido com preço fixo garantido pelo fornecedor.

## Fluxo verificável

1. Na revisão do formulário, exigir usuário autenticado e validar todos os campos no backend. O UID vem da sessão verificada, nunca do payload do aplicativo.
2. Criar uma operação com ID único, dados normalizados, hash do conteúdo, reserva de orçamento e cotas em transação. Toques repetidos retornam a mesma operação; reutilização do ID com conteúdo diferente é rejeitada.
3. Enfileirar o trabalho privado. O aplicativo observa etapas reais: aguardando, gerando, armazenando imagem, enviando, salvando, concluído ou falha. Sem porcentagem fictícia.
4. O backend monta um prompt de estrutura fixa, com os campos do herói como dados delimitados, incluindo nascimento somente se preenchido. Modelo, qualidade, quantidade e tamanho são controlados pelo servidor. A descrição não pode mudar o fluxo nem executar ferramentas.
5. Após receber a imagem, persistir imediatamente em armazenamento temporário privado e registrar a etapa. Fazer upload para Cloudinary com identificador determinístico e sem sobrescrita; gravar o herói com a mesma identidade de operação. Repetir upload/gravação é idempotente.
6. Após sucesso, exibir o herói personalizado e limpar o temporário. Falha de upload/gravação oferece retomada sem custo de nova geração. Resultado de geração incerto, ou queda antes da persistência da imagem, não repete a OpenAI automaticamente: requer conciliação/consentimento para uma nova tentativa.

Estrutura proposta: `users/{uid}/heroCreationJobs/{operationId}` e `users/{uid}/heroes/{heroId}`. A imagem final é uma referência de ativo Cloudinary, sem segredos. Regras permitem leitura somente pelo proprietário; criação/conclusão dos registros fica no backend. A função que usa Admin SDK verifica identidade e propriedade explicitamente. O cliente não altera etapas, custos, cotas, proprietário nem referência da imagem.

Imagem Cloudinary autenticada com obtenção de URL de download assinada e temporária somente após verificar proprietário. Não expor segredo, upload sem assinatura ou URL permanente pública no APK. Cache local de imagem/estado deve ficar restrito à conta e ser limpo ao sair. Credenciais administrativas ficam em Secret Manager; não pedir senhas/chaves na conversa.

A chave ComicVine permanece fora do APK. No novo backend, a validação dos IDs de origem/poder usa a chave no Secret Manager e cache de catálogos reais. A tradução ML Kit já aprovada continua local; não há novo provedor de tradução.

## Configuração necessária — até seis passos

1. Confirmar a proposta acima. Informar apenas o Firebase project ID e o Cloudinary cloud name, caso as contas já existam; são identificadores públicos. Não enviar chaves/senhas.
2. No Firebase, registrar `com.example.app_marvel`, habilitar E-mail/Senha e colocar `google-services.json` em `app/` localmente ou no secret de CI `GOOGLE_SERVICES_JSON`. Neste workspace o arquivo ainda está ausente; não se inferiu a configuração do seu console.
3. Ativar Firestore, Functions/Cloud Tasks e armazenamento temporário privado no projeto Blaze aprovado; selecionar uma região comum aos recursos onde suportado e configurar alertas de infraestrutura. Regras/IAM serão entregues para revisão antes do deploy.
4. No projeto OpenAI, configurar faturamento, verificar disponibilidade de `gpt-image-2` e os limites da conta. Preparar credencial restrita no ambiente privado; um teste de geração só ocorrerá após aprovação dos custos/limites.
5. Configurar `OPENAI_API_KEY`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` e a chave ComicVine no Secret Manager via console/CLI local, sem colar valores no chat nem versionar. Cloudinary cloud name fica como configuração pública. Não há pedido de JSON de conta de serviço.
6. Depois de receber a configuração e validar a implementação, revisar o deploy e executar uma única criação autorizada, seguida de retomada de operação/checagem de titularidade sem nova geração. Registrar custo observado e evidências. O piloto permanece desabilitado até as configurações e aprovações estarem completas.

## Aceite e escopo

Bloco funcional seguinte: revisão → autenticação/retorno ao rascunho → confirmação de custo → geração → upload → gravação → apresentação do herói criado. Critérios: segredos ausentes no APK/logs; identidade/propriedade verificadas no serviço; cotas/orçamento concorrentes; toques duplicados sem duplicação; retomada sem nova imagem; campos preservados; estados reais e falhas compreensíveis. A coleção completa e edição de heróis podem ser um bloco posterior, mantendo imagem fixa.

Não implementar backend antes de confirmar a proposta, conforme a instrução original: “Se não existir backend para operações que exijam segredo, proponha uma solução mínima e confirme antes de implementá-la.” O pedido de confirmação se aplica especificamente ao novo serviço e às decisões de custo/direção visual, não a uma nova aprovação do bloco 13.

## Fontes oficiais consultadas

- https://developers.openai.com/api/docs/models/gpt-image-2
- https://developers.openai.com/api/docs/guides/image-generation — suporte/tamanhos e referência de custo de saída
- https://developers.openai.com/api/docs/pricing — tokens de entrada e imagem
- https://developers.openai.com/cookbook/examples/multimodal/image-gen-models-prompting-guide — estrutura de prompt
- https://firebase.google.com/docs/functions/ — deploy no plano Blaze
- https://firebase.google.com/docs/functions/task-functions — fila, controle de concorrência e repetição
- https://firebase.google.com/docs/functions/config-env — Secret Manager
- https://cloudinary.com/documentation/image_upload_api_reference — ativos autenticados e download assinado temporário
