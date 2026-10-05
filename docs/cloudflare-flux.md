# Geração de imagens — Cloudflare FLUX.2 klein 9B

A geração agora usa `@cf/black-forest-labs/flux-2-klein-9b` no Workers AI, via REST no backend Firebase local. Cloudinary, persistência, confirmação da tentativa, orçamento/cotas e retomada continuam com o fluxo existente.

No arquivo privado `backend/.env.local`, substitua a configuração OpenAI por:

```dotenv
CLOUDFLARE_ACCOUNT_ID=
CLOUDFLARE_API_KEY=
```

`CLOUDFLARE_ACCOUNT_ID` é o identificador de 32 caracteres hexadecimais da conta. Apesar do nome solicitado, `CLOUDFLARE_API_KEY` deve conter um **API Token**, usado em `Authorization: Bearer`, com permissão Workers AI Write na conta selecionada. Uma Global API Key exigiria também e-mail, portanto não atende a este contrato de duas variáveis. As chaves permanecem no backend; não envie valores à conversa ou ao Git.

Execute `npm ci --prefix backend` para instalar a dependência de conversão de imagem. Reinicie os emuladores para carregar o ambiente. `npm run providers:status` agora apresenta `cloudflare`; `--connections` consulta o catálogo de modelos da conta, sem inferência. Encontrar o modelo não prova autorização/cota para a primeira geração.

A inferência envia um formulário multipart com prompt, width=1024 e height=1536; o modelo usa quatro passos fixos, sem o parâmetro OpenAI `quality: low`. A resposta esperada é `success: true` e `result.image` em base64. O backend valida limite, formato, dimensões e imagem estática e normaliza JPEG/PNG/WebP para PNG usando Sharp, preservando o contrato com Storage, Cloudinary e Android. Não há repetição automática nem fallback para OpenAI. Transporte, 5xx e resposta inválida continuam como resultado incerto; rejeição 4xx é falha de geração.

A política sobe para versão 2. Repetir a preparação de uma operação antiga ainda em `prepared` migra seus parâmetros privados transacionalmente; operações já despachadas/concluídas não são alteradas. O Android mantém o mesmo fluxo e interface. A reserva conservadora de US$ 0,05 por tentativa e os demais limites são controles existentes, não preço observado do Cloudflare.

Validação local: diagnóstico com Auth Emulator e callable real do SDK; decodificação/conversão com Sharp e rejeição de dimensões/conteúdo inválidos. Functions Emulator nativo continua bloqueado pelo socket Unix neste ambiente; a CI focal verifica preparação, reserva e diagnóstico. Geração externa e upload reais ainda dependem das credenciais privadas e não foram executados nesta refatoração.

Referências: [modelo](https://developers.cloudflare.com/workers-ai/models/flux-2-klein-9b/), [multipart e parâmetros](https://developers.cloudflare.com/changelog/post/2026-01-28-flux-2-klein-9b-workers-ai/), [consulta do catálogo e autenticação](https://developers.cloudflare.com/api/resources/ai/subresources/models/methods/list/).
