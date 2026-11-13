# VixxLauncher — etapa visual mobile

Esta etapa aplica a identidade visual Vixx à árvore Android realmente incluída pelo Gradle (`VixxLauncher/VixxLauncher`).

## Alterações
- Painéis escuros com gradiente violeta e borda roxa neon na tela inicial e no gerenciamento de versões.
- Novos drawables de hero/tile para uso visual consistente no módulo Android.
- Preservados IDs e controles de instalação/seleção existentes; não foram adicionados atalhos sem callback funcional.
- A pasta `VixxLauncher/src` na raiz do repositório contém uma sobreposição de recursos, mas não é o módulo de app incluído por `settings.gradle.kts`; alterações visuais ativas devem ir para `VixxLauncher/VixxLauncher/src/main`.

## Desempenho e compatibilidade Mali
A aparência do launcher não aumenta o FPS do Minecraft. O desempenho depende do modelo Mali, driver, RAM, versão do Android, renderizador/bridge e configuração gráfica. Validar 1.8.9 e versões 1.19–1.20 em dispositivos reais, comparando estabilidade, temperatura e FPS; evitar promessas de desempenho universal. Esta etapa não modifica renderizador, JVM ou parâmetros gráficos.

## Validação
O projeto é Android/Gradle e não possui preview web nesta área de trabalho. Faça build do APK no ambiente Android configurado e teste em aparelho/emulador; confira a tela inicial, seleção/instalação de versão e início do jogo. Não distribuir keystores, senhas ou tokens. Preserve licenças de terceiros.
