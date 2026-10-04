# Bloco 2 — splash, acesso à conta e painel contínuo

## Correção visual solicitada

A revisão atual restaura SUA centralizado sobre o símbolo MARVEL no Login/Cadastro e na marca menor do cabeçalho principal, com o mesmo asset exportado. Login usa título de 40sp, rótulos separados de 32sp, campos com contorno branco e links Bebas Neue, conforme o contexto salvo. Marv permanece discreto junto ao título. A splash nativa recebe a marca completa; não é a sequência de três frames do Figma, cujo contexto completo não está salvo.

Avisos de desenvolvimento removidos das telas. Formulários começam em READY; validação continua ativa. Erros operacionais aparecem somente após tentativa de envio. Firebase sem configuração nunca autentica ficticiamente. Seções sem dados mantêm apenas apresentação/navegação; o estado interno de integração não vira aviso ou lista fictícia.

A verificação da versão anterior está registrada abaixo; a compilação/prévia desta revisão será registrada ao concluir.

## Resultado

- Splash nativa AndroidX, na MainActivity, sem Activity adicional ou espera artificial.
- Login e cadastro em AuthFragment (layout compartilhado, argumentos distintos), ViewModel/SavedStateHandle e repositório Firebase Authentication real.
- Cadastro: nome, e-mail, senha, confirmação; login: e-mail e senha. Validação por campo, envio único enquanto pendente, indicador indeterminado e mensagens PT-BR para rede, credenciais, senha, limite e indisponibilidade.
- Senhas somente em memória na edição/rotação; nome/e-mail restauráveis após morte do processo. EditText de senha não salva seu estado.
- Sessão existente abre Início. Sucesso remove formulário da pilha. Área Perfil permite entrar quando visitante e mostra somente nome/e-mail reais ao autenticar, com saída da conta. Coleção/avatares continuam em seu bloco futuro.
- Explorar sem entrar dá acesso às áreas públicas da fundação; não cria conta anônima nem simula autenticação.
- Painel escuro estendido até o rodapé do celular. Menu inferior desenhado sobre o painel, com fundo a aproximadamente 72% de opacidade observado no cache do Figma. Insets aplicados à posição do menu e ao espaço de rolagem; o fundo não é cortado pela navegação.
- Bebas Neue aplicado diretamente nos títulos para evitar prevalência da fonte geral do tema.

## Referências e decisões

Nenhuma nova consulta ao MCP do Figma. Reutilizado contexto completo Login 30:296 e Home 19:102 salvo no diagnóstico, captura enviada pelo usuário, tokens/ícones/fontes e Marv existentes. O Login restaura a marca central e a tipografia/campos da referência, acrescentando Marv discreto e ações funcionais; não é declarado reprodução pixel a pixel. O cadastro usa a mesma linguagem visual; seu frame não foi consultado. As três telas Splash do Figma não estão em contexto completo no cache: foi usada splash nativa com logo existente, sem inventar sequência temporizada.

AndroidX SplashScreen 1.2.0, Firebase BoM 34.19.0 e Google Services 4.5.0 adicionados para o bloco aprovado. SDKs, applicationId, linguagem e versões estruturais existentes preservados. Java/XML, sem Compose ou código Kotlin do app.

Sem configuração Firebase, Entrar/Criar conta validam os campos e retornam um erro operacional após envio válido; não aparece aviso de desenvolvimento ao abrir a tela. Explorar continua acessível. A configuração e o teste de conta real dependem do proprietário. [Passo a passo](firebase-auth.md).

## Arquivos e dados

| Área | Implementação | Fonte/limite |
|---|---|---|
| Painel/menu/insets | MainActivity, activity_main.xml, callbacks de views | Referência local Home e correção do usuário |
| Splash | tema Starting e splash_brand.xml | Logo original exportado; API nativa |
| Formulários | AuthFragment, fragment_auth.xml, AuthViewModel/Factory/State | Texto de UI local, dados digitados |
| Conta/sessão | AuthRepository/FirebaseAuthRepository/AuthSession e AccountFragment/ViewModel | Firebase UID/displayName/email reais; operações ainda dependem de configuração |
| Disponibilidade de serviços | inicialização FirebaseApp | Sem configuração, falha operacional após envio; nenhum mock |
| Compilação e prévia | workflow e capture-preview.py | APK/lint e capturas reais ADB; sem testes unitários |

Se o perfil de nome não puder ser salvo após criar uma conta, o app informa o resultado parcial e conclui acesso com a conta real existente. Não repete cadastro. Credenciais do Firebase não são publicadas em git; arquivo google-services.json local ou secret de CI.

## Verificações

- Sintaxe dos 25 arquivos Java e 31 XMLs analisados localmente; git diff --check aprovado.
- assembleDebug/lintDebug aprovados no [run 37163811030](https://github.com/LorenzoOliveira-git/app_marvel/actions/runs/37163811030), código 666f18b2. Relatório inspecionado: zero erros e 13 avisos sobre atualizações disponíveis/recursos sem uso.
- App instalado e aberto em emulador Android 35, tela física 320 × 640 px. Capturas de Login, Cadastro, Início, rolagem e fonte 200% inspecionadas: títulos Bebas, painel contínuo e menu translúcido sobre o conteúdo. Cadastro e Explorar foram acessados por rolagem em tela pequena. As primeiras tentativas de captura falharam; a execução citada concluiu o roteiro ADB.
- Essa prévia não cobre todos os destinos, a última posição de rolagem, paisagem, tablet, teclado, TalkBack ou rotação. Esses itens permanecem no roteiro manual.
- Cadastro/login/logout e restauração com conta real ainda não verificados: falta configuração Firebase.
- Nenhum teste unitário adicionado/executado.

## Checklist

| Item | Situação | Evidência / limite |
|---|---|---|
| 1. Fidelidade e componentes | Pendente aceite visual | Login/Home em cache, correção do painel e componentes reutilizados; cadastro/splash adaptados |
| 2. Navegação/hierarquia | Parcialmente verificado | Login → Cadastro → voltar → Explorar → Home executado; pilhas de autenticação real pendentes |
| 3. Regressões | Build/lint aprovados; uso completo pendente | App instalado/aberto e roteiro de prévia concluído |
| 4. Telas/fontes | Parcialmente verificado | Tela pequena e fonte 200% inspecionadas; paisagem/tablet/teclado pendentes |
| 5. Java/MVVM/Fragments/XML | Atendido por inspeção | Interface e dados separados; sem Compose |
| 6. Decisões | Atendido | Bloco 2 aprovado; overlay solicitado; configuração pendente explícita |
| 7. Estados/recuperação | Atendido na implementação; teste real pendente | Operações reais, campos preservados em falha, sem percentual fictício |
| 8. Sem testes unitários novos | Atendido | Apenas diagnósticos/sintaxe, build/lint e ADB |
| 9. ComicVine/derivados | Não aplicável | Nenhum conteúdo oficial neste bloco |
| 10. Credenciais/propriedade | Parcial; teste real pendente | Sem senha no SavedState/logs, Firebase SDK conserva sessão; heróis não existem ainda |
| 11. Apenas Marvel | Não aplicável | Sem catálogo |
| 12. Português/tradução | Atendido na UI | Mensagens PT-BR; nenhuma tradução de domínio |
| 13. Heróis/edição | Não aplicável | Nenhum formulário de herói neste bloco |

## Roteiro manual

1. Sem configuração: abrir app, conferir splash/Login, abrir cadastro e voltar; formulários sem aviso de desenvolvimento; botões ativos para validação, sem login fictício.
2. Explorar sem entrar: abrir Home e cada destino. O painel deve continuar abaixo do menu até o rodapé. Rolar ao último card: ele deve ser alcançável sem ficar preso atrás do menu.
3. Girar aparelho e usar fonte 200%, largura 320dp, paisagem e tablet. Conferir textos, menu sobreposto e rolagem. Abrir teclado no cadastro: campo/ação devem continuar acessíveis ao rolar.
4. Com Firebase configurado: enviar campos vazios, e-mail inválido, senha menor que seis caracteres e confirmação diferente: erros por campo, nenhum envio.
5. Criar conta sua válida: progresso real, Home e nome/e-mail no Perfil; usuário em Authentication > Users. Cadastro de e-mail já usado não deve criar duplicata.
6. Sair e entrar: remover acesso atual, conferir credenciais inválidas e depois válidas; reabrir app autenticado abre Home. Voltar do Android não deve reabrir cadastro concluído.
7. Cortar a internet em tentativa de acesso: conferir mensagem e campos preservados. Girar durante envio: sem nova requisição automática. Após morte do processo, nome/e-mail são restaurados e senha deve ser digitada de novo.
8. TalkBack: campos/erros/botões anunciados, aviso de envio/falha lido; Marv decorativo não duplica anúncio. Navegação por gestos e três botões: ações fora da barra sistema.

## Pendências e próximo bloco

Configurar Firebase e verificar contas reais antes do aceite completo da autenticação. Após validação, próximo bloco proposto: Home e catálogo de personagens com ComicVine, subdivididos; primeiro confirmar acesso à API, filtros Marvel e o significado das últimas atualizações. Nenhum serviço futuro foi simulado.

## Instalar APK de inspeção

Os certificados de depuração do bloco 1 e do primeiro build do bloco 2 foram comparados e são diferentes. Desinstale a APK anterior antes de instalar esta entrega; isso apaga os dados locais daquela instalação. Não havia conta/coleção real no bloco 1. A APK sem Firebase continua apenas com inspeção e exploração; nunca cria usuário fictício.
