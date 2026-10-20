# Integração das ideias do Ryujinx Neo

Crédito pela investigação original e pelas implementações de referência:
**Sunshinin11, Ryujinx Neo e os contribuidores de Ryujinx/Kenji-NX**.
O Moonwitch adapta essas ideias à sua base C++/Vulkan; não reivindica a autoria
das descobertas. As correções do Lemon mantêm seus créditos integrais separados
em [LEMON_PROJECT_PORTS.md](LEMON_PROJECT_PORTS.md).

## Gather bias no Turnip

Referência: [734fe55f8f7e2d2ae69449374476e360521e4d51](https://github.com/Sunshinin11/Ryujinx_Neo/commit/734fe55f8f7e2d2ae69449374476e360521e4d51).
Autor: Sunshinin11 <151484018+Sunshinin11@users.noreply.github.com>.
O commit original também registra Claude Opus 5.5 <noreply@anthropic.com> como coautor.

Estende ao Turnip a compensação de arredondamento de texture gather já existente
no backend SPIR-V. O deslocamento é meio passo de subtexel do host, calculado com
`subTexelPrecisionBits`, dividido pelo tamanho da textura. Os demais drivers
mantêm o deslocamento anterior de 1/512 texel.

A referência trata de blocos pretos na máscara de sombras/profundidade de TOTK.
Não prova que gather bias resolva a grama desaparecendo. A correção de coerência
de memória do Lemon permanece ativa. A versão do cache Vulkan passa de 19 para
20, para não reaproveitar shaders antigos sem a compensação; a primeira execução
terá de reconstruir esse cache.

## Graphics Pipeline Library

Referência: [a3759eab774c8e1f98aeb31aff19ae158f0f9cdc](https://github.com/Sunshinin11/Ryujinx_Neo/commit/a3759eab774c8e1f98aeb31aff19ae158f0f9cdc),
`PipelineLibraryCache.cs` e `PipelineState.cs`.

Implementação C++ para a arquitetura do Moonwitch: bibliotecas separadas para
entrada de vértices, shaders anteriores à rasterização, fragment shader e saída
do fragmento. A ligação rápida reutiliza bibliotecas já compiladas. As chaves
comparam SPIR-V completo, definição do layout de descritores e estados relevantes;
não usam endereços temporários ou apenas hashes de shader como identidade.
Render passes usam seus handles estáveis, conservadoramente evitando reutilização
entre passes diferentes. Estados dinâmicos também participam da chave.

Ativação inicial somente em Turnip com `VK_KHR_pipeline_library`,
`VK_EXT_graphics_pipeline_library`, a feature habilitada e
`graphicsPipelineLibraryFastLinking`. Drivers sem suporte mantêm pipelines
monolíticos. Falha de biblioteca ou estado ainda não reconhecido desativa esse
caminho na sessão e volta à criação monolítica. Rasterizer discard ativo também
usa o caminho tradicional. Não há alteração de interface ou de clocks.

O cache usa LRU: acessos renovam a posição da biblioteca e o descarte remove a
menos recentemente usada. Há até 2048 entradas concluídas e 64 MiB de cópias de
chaves (mapa e lista). Entradas em compilação não são descartadas. Uma única thread
produz cada chave; concorrentes compartilham o resultado ou a exceção. Uma falha
libera os aguardantes e permite nova tentativa. Esses limites não incluem a
memória interna dos objetos Vulkan nem referências mantidas pelas ligações.

As bibliotecas retêm informações para link-time optimization. A primeira ligação
continua rápida; um worker de baixa prioridade prepara o pipeline otimizado e
publica seu handle atomicamente. A seleção de versão participa da decisão de
vincular o pipeline, inclusive quando o jogo mantém o mesmo shader. O pipeline
rápido permanece vivo até o encerramento normal, preservando command buffers
já gravados. A otimização não altera o layout de descritores.

A fila tem limite de 64 trabalhos em andamento/pendentes para não reter uma
quantidade ilimitada de bibliotecas ao carregar caches grandes. Pipelines que
chegam com a fila cheia continuam no caminho rápido. Falha do driver na otimização
preserva o pipeline rápido e interrompe novas tentativas de LTO nessa sessão.
No encerramento, os produtores terminam antes do cancelamento da fila; o trabalho
já executando é aguardado antes de destruir layouts ou salvar o cache final.

O cache Vulkan persistente continua sendo utilizado. Os testes automatizados usam
um driver simulado para verificar concorrência e duração dos objetos; eles não
substituem a validação de imagem/desempenho no aparelho.

## Licença da referência

O aviso MIT completo é preservado em
[licenses/RYUJINX_NEO_MIT.txt](licenses/RYUJINX_NEO_MIT.txt).
Os avisos e licenças dos arquivos preexistentes do Moonwitch são mantidos.
