# Marble / EvoX 12.3 — Full LTO e GPU 591,5 MHz

Projeto exclusivo para **EvolutionX-17.0-20261010-marble-12.3-Official**.
A compilação usa o mesmo commit, a configuração extraída da imagem oficial e o mesmo Clang da ROM. Full LTO já está habilitado nessa base. O overclock é aplicado ao DTB original, alterando exclusivamente a frequência superior do Adreno 725 de 580 para 591,5 MHz. Voltagens e votos de barramento originais são mantidos. Não há promessa de estabilidade validada no aparelho.

## Pacotes gerados

- `Marble-EvoX12.3-FullLTO-GPU591.5.zip`: instalar pelo recovery compatível com a EvoX. Substitui o kernel de boot e modifica o DTB em vendor_boot. O instalador exige o hash do DTB original validado ou de uma instalação anterior deste pacote.
- `Marble-Profiles.zip`: instalar no Magisk e reiniciar. O botão **Ação** alterna os perfis. O perfil inicial é **balanced**. O último perfil selecionado é mantido após reiniciar.
- `Marble-Kernel-Sources.zip`: scripts, patch equivalente no código-fonte e configuração usada. Não é instalável.

Os backups de boot e vendor_boot ficam em `/sdcard/MarbleKernelBackup/`. Para voltar, restaure o par de backups do mesmo slot pelo recovery. Desative/remova o módulo de perfis e reinicie para encerrar seus ajustes. Atualizações da ROM podem exigir uma nova build deste projeto.

| Perfil | GPU mínima–máxima | CPU mínima–máxima |
|---|---|---|
| powersave | 220–364 MHz | menor frequência–65% do máximo original |
| balanced | 220–515 MHz | menor frequência–100% |
| performance | 439–591,5 MHz | 40%–100% |

As frequências de CPU são arredondadas para valores disponíveis em cada cluster. Os limites térmicos permanecem operacionais. O controlador reaplica os limites a cada 10 segundos quando necessário, pois a ROM também pode escrever nesses controles. Não garante exclusividade contra outros gerenciadores de desempenho.

## Personalização

Edite, com root, `/data/adb/modules/marble_profiles/profiles.conf`. Frequências de GPU estão em Hz; CPU em percentuais do máximo original. Os valores de GPU precisam existir na tabela do kernel. Para travar a GPU a 591,5 MHz no perfil performance, altere `performance.gpu_min` para `591500000`.

Aplique sem reiniciar:

```sh
su -c '/data/adb/modules/marble_profiles/profilectl apply performance'
su -c '/data/adb/modules/marble_profiles/profilectl status'
```

Troque `performance` por `balanced` ou `powersave` conforme desejado. Valores inválidos são rejeitados antes de alterar os controles.

## Fontes exatas e reprodução

- Kernel: https://github.com/Evolution-X-Devices/kernel_xiaomi_sm8450 — commit `c3d1c3d93062911060e8460fdb88cab235f6603e`.
- Device trees: https://github.com/Evolution-X-Devices/kernel_xiaomi_sm8450-devicetrees — commit `9467434dececbf6b01e98e766d92921c35a8c625`; patch equivalente em `patches/`.
- Clang AOSP `r563880c`: https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86 — commit `b54cd2c8e9e0bb057aef3804d42d39c5ac4674c7`.
- AnyKernel3: https://github.com/osm0sis/AnyKernel3 — commit `020dfeccf9d7e962a48400fc94d3e451df92eead`.

O kernel é recompilado sem alterações ao seu código C, para manter a ABI dos módulos da ROM. O ajuste da GPU é separado no DTB e os perfis são um módulo de controle em espaço de usuário. Não são incluídos firmware, imagens completas da ROM, módulos proprietários nem toolchains nos pacotes publicados.

A workflow `.github/workflows/kernel-marble.yml` instala as dependências Ubuntu, reserva swap para Full LTO, executa os testes e chama `scripts/build.sh`. Também é possível rodar esse script em um Linux com as mesmas dependências. A compilação interrompe se a configuração divergir da ROM ou as imagens oficiais mudarem. `build-info.json` registra os hashes e declara explicitamente que o boot não foi testado em hardware.
