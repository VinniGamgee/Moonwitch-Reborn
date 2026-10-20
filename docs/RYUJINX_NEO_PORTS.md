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

O cache é por sessão, limitado a 2048 entradas e 64 MiB de chaves (esse limite de
bytes não inclui a memória interna dos objetos do driver). A compilação ocorre
fora do mutex; corridas podem compilar uma entrada duas vezes, mas só uma é
armazenada. Referências compartilhadas preservam as bibliotecas durante a ligação.
O cache Vulkan persistente do driver continua sendo utilizado.

Esta etapa implementa reutilização e fast linking. A substituição posterior por
pipelines com link-time optimization em segundo plano, presente no Neo, **não**
está incluída: exigiria sincronização adicional com os command buffers em uso.
Não há promessa de ganho de FPS; é preciso verificar a execução no aparelho.

## Licença da referência

O aviso MIT completo é preservado em
[licenses/RYUJINX_NEO_MIT.txt](licenses/RYUJINX_NEO_MIT.txt).
Os avisos e licenças dos arquivos preexistentes do Moonwitch são mantidos.
