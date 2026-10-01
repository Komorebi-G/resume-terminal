# 第三方组件与许可

续端沿用 Moke 依赖或包含的以下第三方组件。感谢这些项目的作者与维护者。

## Vendored（源码内置）

### terminal-emulator / terminal-view
- 来源：[termux/termux-app](https://github.com/termux/termux-app) 的 `terminal-emulator`、`terminal-view` 模块
- 上游来源：[Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator)（Jack Palevich 等）
- 许可：**Apache License 2.0**
- Moke 上游已有修改说明（逐项见各模块的 `README.md`；本分支追加修改见下文）：
  - `terminal-view`：`TerminalRenderer` 增加可选行距倍数 / 字间距（em）参数，`TerminalView` 增加 `setFontSpacing(...)`；
    `TerminalView` 增加全屏程序内的滑动处理与滚屏回调，新增 `MokeScroll.java`（滑动决策）；文本选择工具条去掉无作用的 "More…" 项。
  - `terminal-emulator`：将 `TerminalSession.java` 改写为传输无关（面向新增的 `TerminalTransport.java`，不再 fork 本地 shell）；
    保留 `JNI.java` 与 `src/main/jni/termux.c`（PTY 子进程，`JNI` 改为 public），用于以独立子进程运行 `mosh-client`；
    `TerminalEmulator.java` 识别 DECSET 1003 并新增 `isBracketedPasteMode()`。其余文件未修改。

### 字体（打包进 APK）
- [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono)（`res/font/jetbrains_mono.ttf`）——**OFL**，默认等宽主字体，未修改。
- [Noto Sans SC / 思源黑体](https://github.com/notofonts/noto-cjk)（`res/font/noto_sans_sc.otf`）——**OFL**，默认中文回退。为控制体积，**子集化**为常用汉字（GB2312 + 常用标点，约 2.9MB），非完整字符集。
- [Noto Sans Symbols 2](https://github.com/notofonts/symbols)（`res/font/noto_sans_symbols2.ttf`）——**OFL**，作**符号回退字体**（合成链末级），补主/回退字体常缺的媒体 / 几何 / 杂项符号等终端字形，未修改。
- [Symbols Nerd Font Mono](https://github.com/ryanoasis/nerd-fonts)（`res/font/symbols_nerd_font.ttf`）——**MIT**（nerd-fonts 项目；所含图标集各自的许可见其仓库 `LICENSE`），作 **Nerd 图标回退字体**（合成链末级），补 powerline 分隔符 / devicons / 文件类型图标等私有区字形，取官方 v3.4.0 的 `SymbolsNerdFontMono-Regular.ttf`，未修改。
- [Maple Mono NF CN](https://github.com/subframe7536/maple-font)（`res/font/maple_mono.ttf`，**仅 `maple` 发行变体**）——**OFL**，maple 变体的默认中文回退（中英等宽 + Nerd 图标，取官方 v7.9 的 NormalNL-NF-CN unhinted Regular 单字重），未修改；standard 变体不打包（见下表可运行期下载）。

### 可下载字体（运行期按需下载，不打包进 APK）
用户在"设置 · 字体"中可选择下载。均从各项目官方 GitHub Releases 获取，存于 app 私有目录：

| 字体 | 许可 | 来源 |
|---|---|---|
| [Fira Code](https://github.com/tonsky/FiraCode) | OFL | 连字编程等宽（Regular） |
| [Maple Mono NF CN](https://github.com/subframe7536/maple-font) | OFL | 高分屏 unhinted 变体（NormalNL-NF-CN），含中文 2:1 + Nerd 图标 |
| [Hack](https://github.com/source-foundry/Hack) | MIT-derived (Hack Open Font License) | 经典编程等宽，无连字 |
| [IBM Plex Mono](https://github.com/IBM/plex) | OFL | 等宽（Regular，来自 google/fonts） |
| [Source Code Pro](https://github.com/adobe-fonts/source-code-pro) | OFL | 等宽（可变字重，来自 google/fonts） |
| [Roboto Mono](https://github.com/googlefonts/RobotoMono) | Apache-2.0 | 等宽（可变字重，来自 google/fonts） |
| [Ubuntu Mono](https://design.ubuntu.com/font) | UFL | 等宽（Regular，来自 google/fonts） |
| [Inconsolata](https://github.com/googlefonts/Inconsolata) | OFL | 等宽（可变字重，来自 google/fonts） |
| [Space Mono](https://github.com/googlefonts/spacemono) | OFL | 等宽（Regular，来自 google/fonts） |
| [Anonymous Pro](https://www.marksimonson.com/fonts/view/anonymous-pro) | OFL | 等宽（Regular，来自 google/fonts） |
| [Cascadia Code](https://github.com/microsoft/cascadia-code) | OFL | 连字等宽（可变字重，来自 google/fonts） |
| [JetBrainsMono Nerd Font](https://github.com/ryanoasis/nerd-fonts) | OFL（图标集见 nerd-fonts） | JetBrains Mono + Nerd 图标（Regular） |
| [Victor Mono](https://github.com/rubjo/victor-mono) | OFL | 连字 + 草书斜体（可变字重，来自 google/fonts） |
| [DejaVu Sans Mono](https://github.com/dejavu-fonts/dejavu-fonts) | Bitstream Vera License | 字形覆盖广的等宽（Regular，官方 Release） |

用户在"设置 · 字体 · 上传本地字体"导入的 TTF / OTF 文件仅存于设备本地、不随 APK 分发，其许可由用户自行负责。

## 运行期依赖（构建时拉取，未内置源码）

| 组件 | 许可 | 用途 |
|---|---|---|
| [sshj](https://github.com/hierynomus/sshj) | Apache-2.0 | SSH 传输 |
| [Bouncy Castle](https://www.bouncycastle.org/) (`bcprov-jdk18on`, `bcpkix-jdk18on`) | MIT-style (Bouncy Castle License) | 加密算法（sshj 依赖） |
| [EdDSA-Java](https://github.com/str4d/ed25519-java) (`net.i2p.crypto:eddsa`) | CC0-1.0 | ed25519 密钥（sshj 依赖） |
| AndroidX / Jetpack Compose / Material3 | Apache-2.0 | UI 框架 |
| Kotlin 标准库与协程 | Apache-2.0 | 语言运行时 |

## mosh native

mosh 集成：`libmosh-client.so`（mosh 1.4.0 前端 + [rjyo/mosh-android](https://github.com/rjyo/mosh-android) 预编译静态库）
作为**独立可执行二进制**（GPLv3），以独立子进程 + PTY/管道 IPC 运行，**不与产品层链接**。
二进制不入库，由 `scripts/build-mosh-native.sh` 从公开源码复现。分发含该二进制的 APK 须随附对应 GPLv3 源码；商业分发前请法务确认。

## 续端使用的 Linux 配套程序

- [zmx](https://github.com/neurosnap/zmx)，0.8.1，MIT 许可；由安装脚本从官方分发站下载独立二进制，不打包进 APK。校验值见 `app/src/main/assets/companion/install.sh`，安装说明见 `docs/SETUP.md`。APK 内置本项目的安装脚本和入口脚本，zmx 二进制仍由远端单独下载。
- 本产品基于 [Moke](https://github.com/briqt/moke)，上游提交 `2aee58b3159b91517632eaac981205fd5b692459`，GPL-3.0-or-later；保留源代码的著作权与许可声明。

## 续端对终端组件的追加修改

2026-09-30 至 2026-10-01，本分支修改 `TerminalEmulator` 的粘贴控制字符过滤、`TerminalTransport` 的 exec 超时接口、`TerminalView` 的 IME 和硬件复制粘贴路径及 `TextSelectionCursorController` 的选区处理，并补充回归测试。这些终端组件及其测试继续采用 Apache-2.0，原作者通知保留。Android 产品层和本分支配套安装/入口脚本采用 GPL-3.0-or-later；独立下载的 zmx 仍采用自己的 MIT 许可。
