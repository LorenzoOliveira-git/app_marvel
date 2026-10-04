# APK de demonstração

Em 04/10/2026, o usuário autorizou dados mocados para testar o trabalho atual sem configurar APIs.

Compile com `./gradlew :app:assemblePreview`. O APK fica em `app/build/outputs/apk/preview/app-preview.apk`, assinado para depuração, compatível com Android 7.0/API 24 ou superior e sem exigir chave ComicVine ou configuração Firebase. A versão se chama **Sua Marvel Preview** e tem applicationId `com.example.app_marvel.preview`, permitindo instalação ao lado da versão normal.

Para entrar, use qualquer e-mail válido e uma senha preenchida. Para cadastrar, preencha nome, e-mail, senha de pelo menos seis caracteres e confirmação igual. A sessão e a última identidade cadastrada ficam somente no aparelho; senhas não são armazenadas ou verificadas por servidor. A sessão permanece após fechar o app; **Sair da conta** encerra a sessão. Use dados de teste.

O repositório local de autenticação existe somente em `src/preview`: `debug` e `release` mantêm o Firebase. O visual usa os mesmos layouts e recursos principais, preservando splash, marca SUA sobre MARVEL, Marv e painel até a parte inferior com navegação sobreposta. Não acrescenta avisos de configuração às telas.

O APK inclui tudo que foi implementado até esta etapa: autenticação/formulários, Home, destinos de navegação e infraestrutura ComicVine/ML Kit/cache. As páginas de catálogo, criação de herói e coleção ainda não foram implementadas por completo; este pedido de compilação não as transforma em funcionalidades concluídas. O mock atende os fluxos existentes de identidade e perfil, sem inventar um contrato ComicVine. A tradução continua sendo a implementação real do ML Kit, sem resultado simulado; conteúdo novo precisa de modelo baixado, enquanto o cache pode funcionar offline.

O workflow compila e verifica lint de `debug` e `preview`, compila também as classes `release`, mantém as verificações de navegação e ML Kit e verifica login/cadastro/persistência/saída da demonstração com a rede desligada. Também confere que o mock não está no APK normal. Os artefatos incluem o APK completo, partes para transferência e SHA-256 para verificar a reconstrução do arquivo.
