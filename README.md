<p align="center"><img src="https://raw.githubusercontent.com/GregTechCEu/Branding/refs/heads/master/gregtech_ceu_modern_logo_large_modern.png" alt="Logo"></p>

# GregTech-Modern (CTNH Modified)

[![Build](https://github.com/CTNH-Team/GregTech-Modern/actions/workflows/build.yml/badge.svg?branch=dev)](https://github.com/CTNH-Team/GregTech-Modern/actions/workflows/build.yml)

Modified version of GregTech-Modern, used only in Create: New Horizon (CTNH) modpack. Forked from [GregTechCEu/GregTech-Modern#`v7.4.1-1.20.1`](https://github.com/CTNH-Team/GregTech-Modern/tree/v7.4.1-1.20.1).

## Building

This mod can be built under [CTNH-Team/CTNH-Modules](https://github.com/CTNH-Team/CTNH-Modules) repository using Gradle.

```shell
$ git clone --recursive https://github.com/CTNH-Team/CTNH-Modules.git 
$ cd CTNH-Modules
$ ./gradlew :modules:GregTech-Modern:build            # To build the mod .jar
$ ./gradlew :modules:GregTech-Modern:runData          # To generate data
$ ./gradlew :modules:GregTech-Modern:spotlessCheck    # To check code formatting
$ ...
```

Nightly builds are available on the [Actions](https://github.com/CTNH-Team/GregTech-Modern/actions/workflows/build.yml) page.

Since this mod doesn't depend on other modules, you can also build it independently by cloning the repository and building with Gradle:

```shell
$ git clone https://github.com/CTNH-Team/GregTech-Modern.git
$ cd GregTech-Modern
$ ./gradlew build            # To build the mod .jar
$ ...
```

## License

All code is licensed under the [GNU LGPL v3 License](https://www.gnu.org/licenses/lgpl-3.0.en.html), the same as [upstream](https://github.com/GregTechCEu/GregTech-Modern).

## Credited Works (From GregTechCEu/GregTech-Modern)
- Most textures are originally from [Gregtech: Refreshed](https://modrinth.com/resourcepack/gregtech-refreshed) by @ULSTICK. With some consistency edits and additions by @Ghostipedia.
- Some textures are originally from the **[ZedTech GTCEu Resourcepack](https://github.com/brachy84/zedtech-ceu)**, with some changes made by the community.
- New material item textures by @TTFTCUTS and @Rosethorns.
- Wooden Forms, World Accelerators, and the Extreme Combustion Engine are from the **[GregTech: New Horizons Modpack](https://www.curseforge.com/minecraft/modpacks/gt-new-horizons)**.
- Primitive Water Pump is from the **[IMPACT: GREGTECH EDITION Modpack](https://gt-impact.github.io/#/)**.
- Ender Fluid Link Cover, Auto-Maintenance Hatch, Optical Fiber, and Data Bank Textures are from **[TecTech](https://github.com/Technus/TecTech)**.
- Steam Grinder is from **[GregTech++](https://www.curseforge.com/minecraft/mc-mods/gregtech-gt-gtplusplus)**.
- Certificate of Not Being a Noob Anymore is from **[Crops++](https://www.curseforge.com/minecraft/mc-mods/berries)**.

See something we forgot to credit? Reach out to us on Discord, or open an issue and ask for appropriate credit, we will happily mark it here.
