# APK de prévia visual

`bash gradlew :app:assembleDesignPreview` gera `app/build/outputs/apk/designPreview/app-designPreview.apk`.

Instala como **Sua Marvel · Prévia**, pacote `com.example.app_marvel.preview`, ao lado do app normal. Abre diretamente no início. Catálogo fictício local: personagens, quadrinhos, filmes, séries, arcos, relações e aparições; imagens representadas por blocos coloridos. Busca, filtros e detalhes usam os mesmos componentes da aplicação atual.

Não precisa de chave ComicVine, Firebase ou internet. A variante não inicializa conta, geração paga ou serviços Firebase. Conteúdos fictícios não entram nos APKs debug/release normais; estes continuam usando a API e a tradução reais. A coleção privada continua no estado de visitante nesta prévia.

Workflow `APK de prévia visual` compila, executa lint e percorre as telas em emulador offline, disponibilizando APK e capturas. O APK usa assinatura debug, apenas para avaliação.
