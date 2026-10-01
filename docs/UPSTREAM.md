# 上游来源与个人修改

本项目基于 [briqt/moke](https://github.com/briqt/moke) 的提交 `2aee58b3159b91517632eaac981205fd5b692459`。

**续端是 Moke 的个人习惯特化分支，并非完全原创，也不是上游官方版本。** Moke 原有的 Kotlin / Compose 客户端、主要界面、SSH / mosh 传输、连接管理和设置等构成了本项目的基础。重新命名不改变这些代码的来源与版权归属。

Moke 产品层采用 GPL-3.0-or-later；本分支新增的 Android 产品代码采用相同许可。原作者版权声明与 LICENSE 保留。

终端引擎源自 Termux，Apache-2.0。Linux 配套使用 [neurosnap/zmx](https://github.com/neurosnap/zmx) 0.8.1，MIT；它独立安装，二进制不编进 APK；App 内置本项目的安装脚本与 remote-work 入口，通过 SSH 传送脚本后由主机下载并校验二进制。

本分支针对个人在 Linux / WSL 上使用 Codex、DSH 的习惯，调整品牌、zmx 持久终端恢复、用户目录配套安装、中文草稿输入、复制粘贴保护与快捷键取舍。不是对 Moke 的所有使用场景作通用替代。修改日期和细节见 [CHANGELOG](../CHANGELOG.md)，源码修改处附有日期说明。上游截图是历史参考，不能作为当前 APK 的界面验收证据。关于页保留上游来源入口；本地分支不使用 Moke 的 APK 更新源。

其他依赖和字体许可见 [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md)。

GitHub 仓库：[Komorebi-G/resume-terminal](https://github.com/Komorebi-G/resume-terminal)，以 fork 形式保留上游关联及提交历史。问题反馈与分支维护由本仓库负责，不暗示原作者参与或背书。
