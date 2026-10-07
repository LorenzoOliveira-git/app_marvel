# Validação visual do Arquivo de Papel

A variante auxiliar `designPreview` fornece um catálogo fictício **somente para validação**. O workflow `Validação visual Arquivo de Papel` compila, executa lint e percorre os mesmos componentes do app normal em emulador offline. O resultado publicado são as capturas e o relatório de contraste, sem entrega de APK.

Os dados locais ficam exclusivamente em `src/designPreview`; debug/release normais continuam usando a API, a tradução e a conta reais. A variante auxiliar abre no início e não inicializa conta Firebase nem geração paga.

## Cobertura

| Critério do arquivo de design | Validação |
|---|---|
| Cards, capas e retratos | Início, personagens, quadrinhos, filmes, séries e arcos; proporções e grade de duas colunas |
| Voltar e níveis de navegação | Detalhes internos sem menu inferior; retorno à galeria e manutenção da busca |
| Ausência de dados e textos longos | Personagem sem nome real, origem, imagem ou descrição; nome longo em duas linhas |
| Estados e filtros | Busca sem resultado identifica o termo; limpar recupera os itens; escolhas e cancelamento |
| Texto ampliado e telas distintas | Fonte 160%, largura 320dp e largura 700dp com conteúdo central de até 430dp |
| Áreas de toque | Cabeçalho, busca e filtros com pelo menos 44 × 44dp |
| Contraste | Cálculo dos tokens efetivamente usados sobre papel, folha e pergaminho, inclusive botões desabilitados |
| Perfil e coleção | Firebase local: alteração de nome, segurança do diálogo, falha de rede e retomada; coleção vazia e preenchida; editor e confirmação no servidor |

## Correções encontradas na validação

- A descrição ausente de personagem era ocultada. Agora aparece “Descrição ainda não disponível.”
- Rótulos dos cards passam a informar o tipo e o nome do conteúdo.
- As galerias mostram duração do filme e ano inicial da série quando esses dados existem; identidade ausente usa “Não informado”.
- Perfil usa confirmação verde e mensagem de erro com a cor semântica correspondente.
- Texto branco sobre o fundo claro de botões desabilitados foi substituído por tinta legível.
- O vermelho de foco **#D92D20** permanece intacto. Sobre papel, seu contraste de texto pequeno é **4,36:1**, abaixo dos **4,5:1** exigidos na seção 18 do design. Links usam a variante de texto **#BD281C**; fundos, seleção e destaques continuam com o token original. Este ajuste resolve o conflito entre o token de ação da seção 4 e a exigência de leitura da seção 18 sem mudar a identidade visual.
- O teste de retorno procurava um título acima da região visível. Agora rola explicitamente antes de verificar.
- A abertura do diálogo de descarte fechava o app: um estilo de tipografia substituía o estilo de view do título, removendo dimensões obrigatórias do Material. O título e o corpo agora herdam os estilos de diálogo e recebem a tipografia do design separadamente.
- O teste de cancelamento dependia da posição dos botões do diálogo. Agora verifica “Continuar editando” e “Descartar”, inclusive os dados mantidos no servidor.
- A falha conhecida do Pixel Launcher no emulador é fechada de forma restrita; erros e travamentos do app continuam provocando falha na validação.

## Executar novamente

```bash
python3 .github/scripts/check-design-tokens.py
bash gradlew :app:assembleDesignPreview :app:lintDesignPreview
adb install -r app/build/outputs/apk/designPreview/app-designPreview.apk
python3 .github/scripts/check-design-preview.py
```

O workflow Firebase usa a conta temporária `profile-…@example.test` e duas fichas fictícias no projeto `demo-marvel-local`. A preparação rejeita execução fora dos emuladores Auth e Firestore locais. As alterações feitas pela interface são relidas no servidor; nenhuma imagem é gerada e nenhum provedor pago é acionado. Download de imagem privada real e publicação em produção ficam fora desta validação.

As capturas e os relatórios JSON são publicados nos artifacts dos workflows. A conformidade visual de módulos futuros como favoritos, compartilhamento e onboarding não é afirmada: eles ainda não existem na arquitetura atual.
