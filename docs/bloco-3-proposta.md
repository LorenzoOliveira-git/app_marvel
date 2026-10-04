# Bloco 3 — dados no Android

## Decisões aprovadas

Em 03/10/2026 (horário de São Paulo), o usuário confirmou **Firestore no plano Spark**, **Google ML Kit no Android** e requisições da **ComicVine diretamente no mobile**, sem backend externo neste bloco. A proposta anterior de Functions/Blaze/Cloud Translation foi substituída. A chave ComicVine já existe com o usuário; seu valor não foi recebido nesta sessão.

“Últimas atualizações” da Home significa **HQs recém-publicadas**, conforme decisão anterior. Não usar data de modificação do registro como publicação. Java/XML/MVVM/Fragments, SDKs e versões estruturais continuam iguais. Adicionada apenas a biblioteca oficial `com.google.mlkit:translate:17.0.3`, compatível com o minSdk 24 existente (SDK exige 23+).

## Resultado desta subetapa

- `TranslationRepository` e implementação ML Kit inglês → português (`pt`), com download de modelos via Wi-Fi, falhas tipadas e operações assíncronas.
- Cache SQLite persistente por entidade, campo, hash do original, idioma e versão de processamento/SDK. Original e tradução preservados; alteração do original gera nova entrada. Requisições idênticas em andamento compartilham o resultado. Nenhum dado de conta é enviado para tradução.
- Consulta ao cache precede inicialização/download do tradutor; conteúdo já traduzido pode ser reutilizado sem rede, inclusive após reiniciar o processo. Sem tradução válida, retornar falha; nunca devolver inglês silenciosamente.
- `ComicVineClient`: HTTPS direto, origem fixa, query codificada, timeouts, resposta limitada a 2 MB, redirects desativados, rede serializada fora da thread principal e intervalo mínimo de 1,5 s.
- Limite persistente de 180 tentativas por recurso/hora/aparelho. Não controla a cota global de uma chave usada em vários aparelhos; erros/limites da API permanecem decisivos. Não há retentativa automática nem varredura do catálogo inteiro.
- Chave lida de arquivo privado em `no_backup`, separadamente do APK. Não fica em BuildConfig, recursos, logs, git ou relatórios. O script ADB usa entrada oculta/stdin, sem chave nos argumentos.
- Ferramenta de inspeção e workflow manual recebem a chave privadamente, consultam respostas reais e geram relatório sanitizado. Os filtros da inspeção são sondagens, não suporte presumido no catálogo.
- Serviços registrados em `AppContainer` para os próximos repositórios/ViewModels.

Esta é **infraestrutura funcional de tradução e transporte**, não a entrega completa da Home ou do catálogo. As telas ainda não consomem esses serviços: a entidade Marvel, campos, datas, filtros e paginação dependem da inspeção real. Nenhum conteúdo oficial, biografia, curiosidade ou retrato fictício foi acrescentado. Nenhum aviso de desenvolvimento foi adicionado às telas.

## Firestore Spark

Decisão mantida para dados do usuário nos blocos correspondentes. Esta subetapa não cria coleção de heróis nem grava cache público da ComicVine no Firestore: o cache de tradução é local e não exige cadastro de cartão. Não foi adicionada dependência Firestore sem uma operação concreta a implementar. Configuração do Firebase Authentication continua descrita em [firebase-auth.md](firebase-auth.md).

## Acesso direto e credencial

O Android pode realizar HTTPS sem um servidor intermediário. **Uma chave usada no celular não fica inviolável**: root, instrumentação ou acesso de depuração podem recuperá-la. Provisioná-la separadamente evita distribuí-la dentro do APK, mas não equivale a proteção de backend. O procedimento atual atende uma instalação de depuração para avaliação; não define distribuição pública de uma chave compartilhada.

Esta decisão cobre a ComicVine e tradução local. Não habilita chamadas de geração de imagem com uma chave privada de provedor no APK; essa operação continua no bloco futuro, com orçamento e forma de proteção a definir.

## Configuração da chave — duas opções

Não cole a chave em chat, arquivos de código, issues ou PRs. O APK e o relatório de inspeção não devem conter seu valor.

### Inspecionar a API no computador — caminho imediato

1. Abra um terminal na raiz do projeto com Python 3 instalado.
2. Copie `.env.example` para `.env`, na mesma pasta de `gradlew` e `settings.gradle.kts`, e preencha `COMICVINE_API_KEY` na cópia local. O `.env` é ignorado pelo Git e não deve ser anexado a relatórios. O script usa primeiro a variável de ambiente, depois o `.env` da raiz e, se ambos estiverem vazios, solicita entrada oculta.
3. Execute `python tools/comicvine.py inspect`. Sem `.env` ou variável de ambiente, digite a chave somente na entrada oculta.
4. O script gera `comicvine-inspection.json` sanitizado. Esse relatório pode ser fornecido para validar Marvel, os campos disponíveis e as sondagens de filtros. Falhas HTTP/API são registradas como falhas reais, sem fabricar respostas.

O `.env` serve somente às ferramentas Python locais: o Android não o lê automaticamente, ele não é incluído no APK e não configura os Secrets do GitHub Actions. Valores podem ser literais sem aspas ou entre aspas simples/duplas; não há execução de comandos ou expansão de variáveis.

### Inspecionar pelo GitHub — após incorporar o workflow à main

1. No repositório, abra **Settings → Secrets and variables → Actions → New repository secret**.
2. Crie **`COMICVINE_API_KEY`** e informe o valor nessa interface privada.
3. Em **Actions**, execute **Inspecionar ComicVine sem incluir chave no APK**. O workflow novo precisa estar na branch principal para aparecer na lista manual.
4. Baixe o artefato **comicvine-inspection**. Ele contém somente o relatório sanitizado, sem anexar a chave ao APK. A revisão dos resultados precede implementação dos filtros.

### Configurar o aparelho de depuração

1. Instale o APK de depuração e abra o app uma vez; habilite depuração USB e autorize o computador no aparelho.
2. Com ADB disponível, execute `python tools/comicvine.py device` na raiz do projeto. Se houver vários aparelhos, acrescente `--serial ID_DO_APARELHO`.
3. O script lê a chave da variável de ambiente ou do `.env` local; na ausência desses valores, solicita entrada oculta. Ele grava somente na área privada dessa instalação; desinstalar apaga a configuração.

## Referência visual e dados pendentes

Home `19:102`: contexto já salvo reutilizado. Personagens `38:428`: contexto e screenshot salvos em uma única consulta anterior; geometria em `src/theme/characters-reference.json`. Sem novas chamadas ao Figma nesta subetapa. A tela futura deve preservar carrossel com retrato central maior, nome/metadados, marca SUA sobre MARVEL e painel escuro até o rodapé, com menu sobreposto. Alvos dos filtros devem atingir 48 dp.

| Informação | Fonte/verificação necessária | Regra |
| --- | --- | --- |
| Editora Marvel | Resposta real de editoras e detalhe | Resolver entidade, ID e URL; não presumir número ou só enviar string Marvel. |
| Personagens/retratos | Respostas reais de personagens e relação de editora | Apenas associação Marvel comprovada; dados fictícios do Figma excluídos. |
| Publisher por recurso | Documentação/sondagem e verificação das relações nas respostas | Não tratar um filtro ignorado como aceito. Filtrar antes de exibir e preservar paginação coerente. |
| HQs recém-publicadas | Edições, volumes/editoras e semântica de datas | Não usar atualização de registro; não substituir data de venda por data de capa sem explicitar critério. |
| Origem, times e perfil | Catálogos/relações reais e significado do filtro | Não inventar opções ou semântica de “perfil”. |
| Destaque/curiosidade | Entidade Marvel e fato com fonte | Fixar somente personagem verificado; curiosidade não será inventada ou copiada da referência. |
| Tradução de descrição | Texto seguro da entidade + ML Kit | Não traduzir IDs/nomes como se fossem nomes oficiais; marcar tradução automática e fonte. |

A documentação de recursos da ComicVine continua indisponível nesta sessão. Não foram recebidas respostas autenticadas; o script não constitui evidência até ser executado com a chave. A regra de inclusão e o mapeamento final do catálogo continuam pendentes.

## Verificação e roteiro

Verificações locais: sintaxe Java/Python/JSON/XML e diff. O workflow Android compila/lint, abre os fluxos existentes e executa o ML Kit real no emulador. Uma Activity de diagnóstico existe **somente em debug**, sem menu/launcher; traduz uma frase de orientação e grava resultado privado. O roteiro mata o processo, desliga rede e confere que o mesmo resultado vem do cache SQLite. Nenhum teste unitário ou mock da ComicVine é usado. O resultado efetivo estará nos artefatos `android-data-check` e nas etapas da execução de CI, não deve ser presumido apenas pela presença do roteiro.

Para conferir a integração da API, execute a inspeção real acima. Para conferir UI, abra Login/Cadastro → Explorar → destinos do menu; nenhum aviso técnico deve aparecer. A tradução ainda não é exibida em personagens porque o catálogo não está concluído.

## Próximo trabalho dentro do bloco 3 aprovado

Receber o relatório sanitizado ou executar o workflow após configurar o secret. Validar entidade Marvel, relações, filtros, paginação e publicação. Em seguida, implementar catálogo de Personagens e Home com dados reais e estados de carregamento/falha/recuperação, conectando a tradução sem bloquear imagens ou navegação. Detalhes de personagem ficam na subetapa seguinte. O bloco 3 permanece em andamento.

## Fontes oficiais

- ML Kit Android, versão e download: https://developers.google.com/ml-kit/language/translation/android
- Capacidades/qualidade: https://developers.google.com/ml-kit/language/translation
- Idiomas (`pt` não oferece variante própria `pt-BR`): https://developers.google.com/ml-kit/language/translation/translation-language-support
- Firestore/Spark: https://firebase.google.com/pricing
- ComicVine e termos: https://comicvine.gamespot.com/api/
- Armazenamento privado/HTTPS Android: https://developer.android.com/privacy-and-security/security-tips
