# Correções do Lemon Project no Moonwitch

Todo o crédito pela investigação, desenvolvimento e correções descritas aqui é de
**Ghael-V e do Lemon Project**. O trabalho no Moonwitch é a integração dessas
implementações à sua base existente, não uma correção original do Moonwitch.

- Repositório oficial: https://git.lemon-emu.org/lemon/Lemon-Project
- Espelho: https://github.com/Ghael-V/Lemon-Project
- Autor registrado nos commits: Ghael-V <ghael_v@outlook.es>.
- Os commits de origem também registram `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`;
  essa atribuição é preservada aqui e nas mensagens de integração.
- Os avisos de licença e autoria preexistentes nos arquivos permanecem preservados.

## Lemon 1.2.0 — proteção seletiva de imagens escritas pela GPU

Origem: https://github.com/Ghael-V/Lemon-Project/commit/2c75bf18d0469a1fef2b999776a51729664f2eac

A proteção de leitura passa a ser aplicada às páginas em cache contendo imagens
modificadas pela GPU. A falha de acesso força a atualização da memória antes de a
CPU continuar a leitura. Substitui a opção Use reactive flushing; imagens
pitch-linear mantêm downloads preventivos. Complementa a preservação das imagens
GPU durante invalidação NCE, anteriormente portada do Lemon 1.1:
https://github.com/Ghael-V/Lemon-Project/commit/3b9ed8782de5b2322755d11f9f9734214501f77f

## Lemon 1.2.0 — sincronização da GPU

Origem: https://github.com/Ghael-V/Lemon-Project/commit/23a93b038c5d1de06e0b8b6f3261690a5d5bfb7d

Sinais de fences são entregues ao jogo após a conclusão do trabalho da GPU.
O acquire evita drenar a fila quando o syncpoint do guest já alcançou o valor.
Remove as políticas antigas de GPU Fence Behavior e suas esperas adicionais
nos buffers, seguindo o comportamento do Lemon.

## Adaptação ao Moonwitch

Preservada a interface existente, com remoção somente dos controles obsoletos.
Atualizadas referências nos perfis Android e nas traduções compartilhadas Qt.
Nenhum ajuste de overclock/root está incluído nesta integração.

A compilação verifica a integração; a persistência da grama e o impacto em FPS
precisam ser confirmados jogando TOTK no aparelho.

## Lemon 1.2.0 — reutilização de traduções de shaders

Origem: https://github.com/Ghael-V/Lemon-Project/commit/ba5d601947eed72b1826113b60b243278434376d

Autoria integral da implementação original: Ghael-V / Lemon Project, com a
coautoria registrada acima. Reutiliza traduções caras de fragment shaders e
reduz alocações no SSA. Adaptação Moonwitch: a chave de reutilização inclui
a precisão de shader e a opção de otimização SPIR-V do Moonwitch.
