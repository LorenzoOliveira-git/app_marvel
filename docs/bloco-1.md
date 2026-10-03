# Bloco 1 — Fundação MVVM, navegação e Marv

## Resultado e limites

Implementada a base em Java/XML. MainActivity hospeda o NavHost; HomeFragment mostra a recepção do Marv e os quatro acessos. SectionFragment é reutilizado pelos quatro destinos ainda não integrados, cada um com seus próprios argumentos/estado de navegação. NavigationUI fornece o comportamento de destinos superiores e salvamento/restauração de pilhas.

A home deste bloco é uma recepção provisória da fundação. Notícias, personagem em destaque, curiosidades, catálogos, autenticação e perfil real ficam para seus blocos. Não há mocks autorizados ou implementados.

O escopo aprovado inclui acrescentar o Marv nas telas existentes e futuras quando ajudar a orientar ou informar estado. O Figma não foi modificado.

## Organização

- data/model: enum de destinos AppFeature.
- data/repository: contrato e disponibilidade real do bloco, sem dados de domínio fictícios.
- di/AppContainer: composição das dependências.
- ui/home e ui/section: Fragments e ViewModels.
- ui/common: estado imutável e tradução do modelo para recursos visuais.
- ui/components: FeatureCardView e MarvStateView.
- ui/navigation: contrato de navegação, executado pela Activity.
- res: layouts, navegação, menu, fontes, cores, dimensões e estilos Android.
- src/theme: documentação do Figma, valores e provenance; não é uma estrutura web.

ViewModels não retêm Activity, Fragment, View, Context ou Resources. Observações LiveData usam getViewLifecycleOwner; bindings dos Fragments são liberados em onDestroyView. Não há formulário ou busca neste bloco. A restauração de rolagem/destino ainda precisa ser verificada no aparelho.

## Decisões

- Nome, applicationId, SDKs, Gradle/AGP e opções Java existentes foram preservados.
- Adicionados Navigation Fragment/UI 2.9.5 e Lifecycle ViewModel/LiveData 2.9.4, versões estáveis documentadas.
- View Binding habilitado.
- Identidade escura usada nos dois modos do sistema.
- Botões usam #D71920 para melhorar o contraste do texto branco; ícones selecionados mantêm o vermelho #ED1D24 observado no Figma.
- Títulos Bebas Neue; texto com instâncias estáticas 400/600 derivadas de Inter (opsz 14). Família interna renomeada Marvel UI ao distribuir fontes derivadas; licenças preservadas.
- Conteúdo limitado a 640dp em telas grandes, alturas de texto adaptáveis, rolagem e tamanho em sp.
- Navegação por ícones em telas compactas ou com fonte ampliada; títulos dos itens continuam disponíveis à acessibilidade. Tablets com fonte padrão mostram rótulos.
- Insets de barras, recortes e teclado são aplicados uma vez no contêiner externo.
- Ícones/logo originais exportados a 4x. LayerDrawable mantém os tamanhos individuais de 25/35/35/21/25 × 25dp dentro de um espaço comum de 35dp.
- Marv sorrindo na recepção; pensativo no feedback de indisponibilidade. Imagens derivadas do guia com ImageGen, com alfa e sem novos personagens.
- UiState distingue CONTENT, LOADING, EMPTY, ERROR e UNAVAILABLE. Somente CONTENT (menu local) e UNAVAILABLE são usados agora. Carregamento/erro/retentativa não são simulados.

## Dados e serviços

| Informação | Origem | Estado |
|---|---|---|
| Nomes dos destinos e textos de orientação | Recursos locais em português | Implementado; dados de interface |
| Disponibilidade dos destinos | LocalFeatureRepository | Somente Home disponível neste bloco |
| Personagens, quadrinhos, filmes, séries, arcos | ComicVine | Não integrado; nenhum conteúdo exibido |
| Editora Marvel | Página pública ComicVine 4010-31 | Registro identificado; resposta autenticada/filtros por recurso pendentes |
| Traduções de conteúdo | Provedor ainda não aprovado | Não aplicável ao bloco atual |
| Usuários e heróis personalizados | Firebase/Firestore/Cloudinary futuros | Não integrado |
| Imagens de heróis por OpenAI API | Backend/modelo/custos futuros | Não configurado e não acionado |

A documentação atual da ComicVine não foi acessível no diagnóstico. Endpoints/campos/filtros e relações precisam ser verificados antes do bloco de dados. IDs devem ser preservados, publisher deve ser validado por recurso e descrições devem ser traduzidas com cache. Não há chave no APK ou no repositório.

## Verificações

- Recursos XML analisados e referências locais/XML/Java R verificadas.
- PNGs verificados; assets do Marv com canal alfa (0–255).
- JSON de referências e YAML de CI analisados.
- Modelos, repositório local e UiState compilados com o compilador Java do JDK 17, alvo Java 11.
- Sintaxe das classes Java analisada, sem resolução das bibliotecas Android.
- git diff --check executado.
- Nenhum teste unitário novo. Exemplos do projeto original mantidos.
- Tentativa de assembleDebug/lintDebug interrompida antes da configuração, no download do Gradle: Network is unreachable.
- SDK/emulador não disponíveis localmente; aplicativo não executado. Sem captura real de tela do aplicativo neste ambiente.
- Workflow de build/lint incluído para validação no GitHub Actions; consultar o resultado efetivo do run. Ter o workflow não equivale a compilação aprovada.

## Checklist

| Critério | Situação | Evidência / limite |
|---|---|---|
| 1. Fidelidade e consistência | Pendente validação visual | Contexto Login/Home, recursos originais e tema aplicado; home de conteúdo ainda fora do bloco |
| 2. Navegação e hierarquia | Implementado; validação manual pendente | NavHost, NavigationUI e cinco destinos |
| 3. Ausência de regressões | Pendente | Inspeção/checagens estáticas; build e aparelho precisam confirmar |
| 4. Telas e fontes ampliadas | Pendente validação manual | maxWidth, sp, wrap_content e scroll; sem emulador |
| 5. Java/MVVM/Fragments/XML | Atendido por inspeção | Sem Compose ou fontes Kotlin do aplicativo |
| 6. Decisões confirmadas | Atendido | Bloco 1 aprovado; inclusão do Marv autorizada |
| 7. Estados/recuperação | Atendido na fundação; integrações futuras pendentes | Componente reutilizável; indisponibilidade real, sem erro/carregamento fictício |
| 8. Sem testes unitários novos | Atendido | Apenas testes originais |
| 9. ComicVine e derivados | Não aplicável agora | Nenhum dado de domínio exibido; mapeamento autenticado pendente |
| 10. Credenciais/propriedade | Não aplicável agora | Sem segredos, conta ou herói persistido |
| 11. Conteúdo somente Marvel | Não aplicável agora | Nenhum catálogo exibido |
| 12. Português/tradução | Interface atendida | Textos locais PT-BR; tradução externa não acionada |
| 13. Validação/edição de heróis | Não aplicável agora | Formulário e imagem salva ainda não implementados |

## Roteiro manual de aceite

1. Sincronizar no Android Studio e executar app: aparece Início, recepção do Marv e quatro acessos.
2. Tocar em cada acesso e em cada ícone inferior: título e seleção correspondem ao destino; Marv explica a indisponibilidade sem spinner ou botão de retentativa.
3. Usar Voltar ao início e o Voltar do Android: verificar retorno correto e ausência de destinos duplicados.
4. Rolar a home, mudar de destino e voltar: verificar preservação da rolagem. Girar o aparelho em cada destino: verificar seleção e texto corretos.
5. Colocar o app em segundo plano, encerrar somente o processo em um ambiente de desenvolvimento e restaurar pela tela de recentes: verificar destino/rolagem. Não usar Force stop como simulação de restauração.
6. Verificar largura aproximada de 320dp, tablet e paisagem, fonte 200%, navegação por gestos e três botões: conteúdo rolável, textos legíveis e nada sob barras do sistema.
7. Com TalkBack e teclado: destinos anunciados e selecionáveis; cards anunciados com descrição; imagens decorativas não duplicam o aviso.
8. Conferir modo claro e escuro do sistema: o tema do aplicativo continua escuro e as barras usam ícones legíveis.

## Pendências e próximo bloco

Build/lint e validação no aparelho devem passar antes do aceite. Próximo bloco proposto: splash e autenticação Firebase, incluindo botões Entrar/Criar conta e apoio pontual do Marv. A configuração Firebase será orientada naquele bloco. Provedor de tradução, backend, orçamento/modelo de geração e regras de exclusão não bloqueiam esta fundação.
