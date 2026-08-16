# Maximus Native Exact

Reconstrução nativa Android em Kotlin do aplicativo Maximus Player, otimizada para Android TV Box, controle remoto/D-pad e orientação landscape.

> Este repositório é independente e não modifica o repositório original `ronnie240486/Maximus`.

## Estado do projeto

A base contém a fundação nativa das telas de apresentação, perfis, edição de perfil, Home, catálogo M3U/Xtream, player Media3, rádios, placar, câmeras, ajustes, diagnóstico e favoritos. O fluxo de entrada usa o **ID MAC do dispositivo**, consulta o painel original e recebe as playlists/DNS autorizadas para esse MAC; não existe tela separada para o usuário digitar servidor, usuário ou senha. O parser M3U possui testes unitários para canais, filmes, séries e conteúdo Kids, incluindo a prioridade correta de classificação para grupos como `Séries | Netflix`.

A release atual é uma build de validação técnica do fluxo Native. Ela não deve ser considerada a cópia final do aplicativo original até que os dados reais, a paridade visual, a validação em TV Box e os fluxos completos sejam confirmados.

## Requisitos

- Android Studio ou JDK 21.
- Android SDK com compileSdk 34 e Build Tools 34.0.0.
- Gradle Wrapper incluído no projeto.
- O acesso é ativado pelo MAC exibido na tela inicial e pelo painel do mesmo aplicativo; não configure credenciais Xtream manualmente.

## Build

```bash
./gradlew testDebugUnitTest assembleRelease --no-daemon
```

O APK de release é gerado em `app/build/outputs/apk/release/app-release.apk`.

## Arquitetura

O projeto usa Activities e Views programáticas, sem Compose e sem layouts XML para as telas principais. O package da aplicação é `com.maximus.nativeexact`. A orientação é forçada para landscape e os componentes interativos devem manter foco navegável por D-pad.

## Proteção do repositório original

O repositório original usado como referência visual e funcional permanece somente para leitura. Nenhum commit, arquivo ou configuração dele deve ser alterado por este projeto.
