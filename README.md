<p align="center">
  <img src="https://raw.githubusercontent.com/GregTechCEu/Branding/refs/heads/master/gregtech_ceu_modern_logo_large_modern.png" alt="GregTech CEu: Modern logo">
</p>

<h1 align="center">GregTech CEu: Modern</h1>

<p align="center">面向 Minecraft Forge 1.20.1 的 GregTech Community Edition Unofficial 现代移植版。</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern"><img src="https://img.shields.io/badge/Available%20for-MC%201.20.1-informational?style=for-the-badge" alt="支持的 Minecraft 版本"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-LGPL--3.0-blue?style=for-the-badge" alt="LGPL-3.0 许可证"></a>
  <a href="https://discord.gg/bWSWuYvURP"><img src="https://img.shields.io/discord/701354865217110096?color=5464ec&label=Discord&style=for-the-badge" alt="Discord"></a>
  <br>
  <a href="https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern"><img src="https://cf.way2muchnoise.eu/890405.svg?badge_style=for_the_badge" alt="CurseForge"></a>
  <a href="https://modrinth.com/mod/gregtechceu-modern"><img src="https://img.shields.io/modrinth/dt/gregtechceu-modern?logo=modrinth&label=&suffix=%20&style=for-the-badge&color=2d2d2d&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
  <a href="https://github.com/GregTechCEu/GregTech-Modern/releases"><img src="https://img.shields.io/github/downloads/GregTechCEu/GregTech-Modern/total?sort=semver&logo=github&label=&style=for-the-badge&color=2d2d2d&labelColor=545454&logoColor=FFFFFF" alt="GitHub Releases"></a>
</p>

## 简介

GregTech CEu: Modern（简称 **GTM**）是一个以矿物处理、机器自动化、电力系统和多方块结构为核心的 Minecraft 科技模组。本仓库包含 Forge 1.20.1 版本的模组源码、数据资源和官方文档。

玩家使用说明、配方与机制请访问 [在线 Wiki](https://gregtechceu.github.io/GregTech-Modern/)。模组整合包作者可从 [Modrinth](https://modrinth.com/mod/gregtechceu-modern)、[CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern) 或 [GitHub Releases](https://github.com/GregTechCEu/GregTech-Modern/releases) 获取发布版本。

## 版本与环境

| 项目 | 当前配置 |
| --- | --- |
| Minecraft | 1.20.1 |
| 模组加载器 | Forge 47.4.1 |
| Java | JDK 17 |
| 构建工具 | Gradle Wrapper |
| 许可证 | [LGPL-3.0](LICENSE) |

`gradlew` 会使用项目锁定的 Gradle 版本；无需单独安装 Gradle。请使用完整的 JDK，而不是仅安装 JRE。`gradle.properties` 禁用了 Java 工具链自动下载，因此本机必须已配置 JDK 17。

## 从源码开始

```bash
git clone https://github.com/GregTechCEu/GregTech-Modern.git
cd GregTech-Modern
```

Windows 使用 `gradlew.bat`，macOS/Linux 使用 `./gradlew`。以下命令以 macOS/Linux 为例；Windows 将 `./gradlew` 替换为 `gradlew.bat`。

| 目标 | 命令 | 用途 |
| --- | --- | --- |
| 构建 | `./gradlew build` | 编译、执行检查并在 `build/libs` 生成可发布 JAR。 |
| 启动标准客户端 | `./gradlew runCleanClient` | 使用最小开发依赖启动客户端。 |
| 启动扩展客户端 | `./gradlew runClient` | 启动包含常用联动模组的客户端。 |
| 启动服务器 | `./gradlew runServer` | 在 `run/server` 启动开发服务器。 |
| 运行 GameTest | `./gradlew runGameTestServer` | 执行已注册的 GameTest 后退出。 |
| 生成覆盖率报告 | `./gradlew jacocoTestReport` | 运行 GameTest，并在 `build/coverage` 生成 HTML 和 XML 报告。 |
| 生成数据 | `./gradlew runData` | 将生成资源写入 `src/generated/resources`。 |
| 检查格式 | `./gradlew spotlessCheck` | 校验 Java/Kotlin 代码格式。 |
| 自动格式化 | `./gradlew spotlessApply` | 应用 Spotless 格式化。 |

首次执行时 Gradle 会下载 Minecraft、Forge 和开发依赖。构建产物应从 `build/libs` 获取；`build/devlibs` 中的开发 JAR 不包含嵌入依赖，不能作为发行文件。

### IntelliJ IDEA

1. 用 **Open** 打开仓库根目录中的 `build.gradle`，并选择 JDK 17。
2. 等待 Gradle 导入完成；项目会生成客户端、服务器、数据生成和 GameTest 运行配置。
3. 在 Gradle 工具窗口或运行配置列表中启动相应任务。

贡献代码时必须安装 [Lombok 插件](https://plugins.jetbrains.com/plugin/6317-lombok)；建议同时安装 [Minecraft Development 插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)。如果编辑器未正确解析 Lombok 注解，请确认 IDE 的 annotation processing 已启用。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java` | 模组主代码、API、客户端逻辑与 Forge 集成。 |
| `src/main/resources` | 资源、混入配置、访问转换器及模组元数据模板。 |
| `src/generated/resources` | 数据生成任务产生的资源；属于版本控制内容。 |
| `src/test` | GameTest 和测试辅助代码。 |
| `docs` | 基于 MkDocs 的 Wiki 源文件。 |
| `gradle` | 版本目录、构建脚本和 Gradle Wrapper 配置。 |
| `injected_interfaces` | Forge 接口注入定义。 |

## 为其他模组声明依赖

首先添加 GTCEu Maven 仓库：

```groovy
repositories {
    maven {
        name = 'GTCEu Maven'
        url = 'https://maven.gtceu.com'
        content {
            includeGroup 'com.gregtechceu.gtceu'
        }
    }
}
```

随后在依赖中声明与目标 Minecraft 版本匹配的 GTM 版本：

```groovy
dependencies {
    implementation fg.deobf("com.gregtechceu.gtceu:gtceu-1.20.1:${gtm_version}")
}
```

上例适用于 ForgeGradle 开发环境。若项目使用其他构建插件，请按该插件的重映射/开发依赖机制声明同一 Maven 坐标；不要将开发环境专用的 `fg.deobf` 直接用于发布配置。

## 文档

Wiki 源码位于 [`docs`](docs)。在仓库根目录运行：

```bash
./gradlew mkdocsServe
```

该任务会创建 Python 虚拟环境、安装 [`docs/requirements.txt`](docs/requirements.txt) 中的依赖并启动本地预览。静态站点构建使用 `./gradlew mkdocsBuild`，输出到 `docs/site`。文档页面规范见 [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md)。

## 贡献与反馈

- 提交前请运行 `./gradlew spotlessCheck`，必要时执行 `./gradlew spotlessApply`。
- 修改数据内容后，请运行 `./gradlew runData` 并一并提交生成文件的合理变更。
- 功能问题与错误请通过 [GitHub Issues](https://github.com/GregTechCEu/GregTech-Modern/issues) 反馈；讨论和遗漏署名可前往 [Discord](https://discord.gg/bWSWuYvURP)。
- 大型改动建议先说明目标与实现方向，避免重复工作或破坏兼容性。

## 致谢

- 大部分纹理由 [Gregtech: Refreshed](https://modrinth.com/resourcepack/gregtech-refreshed) 的 @ULSTICK 创作，@Ghostipedia 进行了统一性修改与补充。
- 部分纹理来自 [ZedTech GTCEu Resourcepack](https://github.com/brachy84/zedtech-ceu)，并由社区进行了修改。
- 新材料物品纹理由 @TTFTCUTS 和 @Rosethorns 创作。
- 木质模具、世界加速器和极限燃烧引擎来自 [GregTech: New Horizons](https://www.curseforge.com/minecraft/modpacks/gregtech-new-horizons)。
- 原始抽水机来自 [IMPACT: GREGTECH EDITION](https://gt-impact.github.io/#/)。
- 末影流体链接覆盖板、自动维护仓、光纤和数据银行纹理来自 [TecTech](https://github.com/Technus/TecTech)。
- 蒸汽研磨机来自 [GregTech++](https://www.curseforge.com/minecraft/mc-mods/gregtech-gt-gtplusplus)。
- “不再是菜鸟证明”来自 [Crops++](https://www.curseforge.com/minecraft/mc-mods/berries)。

如有遗漏的署名，请通过 Discord 或 Issue 联系我们，我们会及时补充。
