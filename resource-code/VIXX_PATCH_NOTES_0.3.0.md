# VixxLauncher 0.3.0 — conectividade e HUD

## Alterações deste pacote
- API HTTP agora envia `User-Agent` e `Accept: application/json`, necessários para identificação consistente em APIs públicas como Modrinth.
- Timeout de conexão de 15 s e leitura de 20 s para impedir carregamentos sem fim.
- Falhas de APIs deixam de ser silenciosas: são registradas no log `VixxNetwork` para diagnóstico em vez de parecerem uma busca vazia.
- HUD touch padrão recebeu acabamento de vidro roxo/neon VIXX. Todas as keycodes, posições, tamanhos e ações do layout original foram preservados nesta primeira revisão para evitar perda de controles.
- Workflow CI passa a compilar `:VixxLauncher` ARM64 (em vez do módulo antigo `:ZalithLauncher`) e disponibiliza o segredo `CURSEFORGE_API_KEY` no build.

## Importante sobre serviços online
- Modrinth não exige chave API para pesquisa pública; o User-Agent foi adicionado.
- CurseForge exige uma chave API válida. Configure `CURSEFORGE_API_KEY` em GitHub → Settings → Secrets and variables → Actions. Sem essa chave, resultados do CurseForge não estarão disponíveis. Não coloque a chave diretamente no código.
- A lista de servidores multiplayer dentro do Minecraft usa a pilha de rede do jogo e DNS; este patch não altera o protocolo de servidores. Se continuar falhando, exporte o log de uma tentativa para identificar o erro específico (DNS, timeout, TLS ou servidor).
- O HUD novo do pacote é aplicado ao layout padrão. Se o usuário já tiver um layout personalizado salvo, selecione/redefina o layout padrão Vixx nas configurações de controles para carregar o visual atualizado.

## Segurança e escopo
- Nenhuma chave privada, keystore ou senha foi incluída neste patch.
- Não foram removidos endpoints oficiais de login Microsoft, Minecraft, bibliotecas, assets ou download.
- Não foram feitas alterações arriscadas em renderizadores, bibliotecas nativas ou drivers GPU. Melhorias reais de FPS dependem de testes por renderer, memória e versão do Minecraft.
