# 续端 · Resume Terminal

当前日常基线为未加入同步终端的 **0.1.5**。后续同步与接力探索已停止并归档，版本、安装包和历史工作树入口见 [归档说明](docs/ARCHIVE.md)。

**续端是基于 [Moke（briqt/moke）](https://github.com/briqt/moke) 改进的个人使用习惯特化分支，并非完全原创项目，也不是 Moke 官方版本。** 原有 Android 客户端、主要界面和 SSH / mosh 基础能力来自 Moke；原作者的工作和版权声明予以保留。

本分支主要服务个人在 Linux / WSL 上使用 Codex、DSH 的习惯：跨设备继续终端、中文草稿输入、复制粘贴和简化快捷键。取舍围绕这些场景，不以增加功能数量为目标。

面向 Linux 开发的原生 Android SSH 终端。普通 SSH 可以直接使用临时 shell；配好持久终端后，手机和电脑可以接着操作同一个终端，断开连接时主机继续保留程序和画面。支持 WSL、普通 Linux 和 Linux 虚拟机；主机关机或重启仍会结束进程。

基于 Moke 的 Kotlin/Compose 客户端与 Termux 终端引擎，使用 zmx 保存 PTY。来源与许可见 [UPSTREAM](docs/UPSTREAM.md)。

## 开始使用

先在 Linux 主机开启 SSH，确保手机能连接。安装 App 后添加自己的地址和 SSH 凭据；缺少配套程序时仍可用临时 shell。顶部“持久终端”面板提供“安装并启用”：通过已有 SSH 在当前用户目录安装，支持 Linux x86_64 / ARM64，无需 sudo。成功后新建持久终端；已有临时 shell 不会自动转换。

配套程序需要安装，WSL、普通 Linux 和虚拟机都不会因环境类型而自动自带它。装好后，在电脑上这样开启需要从手机继续的工作：

```bash
cd ~/projects/你的项目
remote-work 项目名
# 在出现的 shell 里运行 codex 或 dsh
```

关闭电脑端终端窗口只断开客户端。运行 `exit` 会结束里面的 shell；手机“持久终端”列表里的“关闭”会结束该终端中的程序。已有普通终端需要以后改从这个入口启动。如果 ~/.local/bin 不在 PATH 中，使用 `~/.local/bin/remote-work 项目名`。

当前版本为 0.1.5 开发版；安装包可按下方步骤从源码构建，文件在 `app/build/outputs/apk/standard/debug/app-standard-debug.apk`。首次添加连接时，填写 Linux 主机地址、端口、用户名和自己的 SSH 密码或私钥；“持久终端”是默认选项。连接后选择已有终端，或输入名称创建一个。之后会记住选择，下次直接恢复；原终端已经结束时会重新显示选择器。

自己的主机连接参数、网络要求和首次验证流程见 [安装与使用](docs/SETUP.md)。

## 手机上常用的操作

| 需要做什么 | 操作 |
|---|---|
| 当前常用组合 | 默认常驻 `⇧←`；“更多 → 全部按键 → 常用键”可改成 Alt+↑ 等组合或 Codex 命令 |
| 选择模型 / 思考强度、恢复对话 | “更多”里的 `/model`、`/resume`；先填入输入栏，按发送执行 |
| 补全与模式切换 | `TAB`；Shift 后按 Tab 发送 Shift+Tab |
| 复制与粘贴 | 长按输出选字，再点“复制”；常驻“粘贴”或 Ctrl+V；多行先进入草稿 |
| 移动和打断 | 倒 T 方向键、“中断”；有选区时按钮改为“复制” |
| Ctrl/Alt/Shift 组合键 | 点按启用，再点取消；长按锁定，锁定时显示小锁 |
| 命令与 Tab 补全 | 打开输入栏，输入前缀后按 Tab；补全后继续输入或直接发送执行 |
| 长命令、中文段落、多行文本 | 输入栏左侧展开多行；快捷键继续保留在下方 |
| 接着使用另一终端 | 顶部“持久终端”入口，选择名称 |
| 字号与显示 | 捏合缩放；设置中调整字体、中文回退字体、配色、行距 |
| 查看历史 | 滑动；智能滚动避免在 Codex 中误触命令历史 |

默认使用中文输入法兼容模式，并请求输入法关闭命令纠错和学习。部分输入法不遵循这些提示，可从终端的键盘入口切换模式。颜色、框线和中英文宽度由真实终端引擎处理。

外接键盘 Ctrl+C 在有选区时复制，无选区时中断当前远端任务；Ctrl+Shift+C 专门用于复制，没有选区也不会中断。Ctrl+V / Ctrl+Shift+V 粘贴；输入栏有草稿时先在本地编辑，Ctrl+A 全选本地草稿，Ctrl+X 剪切选区。复制输出无需关闭草稿栏。

## Linux 配套安装与测试

```bash
sh app/src/main/assets/companion/install.sh  # wsl/install.sh 是兼容入口
python3 wsl/test-persistence.py
python3 scripts/test-linux-companion.py  # 隔离 HOME，实际下载并验证安装与重连
remote-work --list
```

安装器固定 zmx 0.8.1 并校验下载 SHA256。手机和电脑统一使用 `$HOME/.local/state/resume-terminal/zmx`，避免 SSH 与桌面环境不同而看不到同一会话。

## 构建

需要 JDK 17 或更新版本及 Android SDK 35。项目根 `local.properties` 写入 `sdk.dir=/你的/Android/Sdk`。

```bash
./gradlew :app:assembleStandardDebug
./gradlew :app:testStandardDebugUnitTest :terminal-emulator:testDebugUnitTest :terminal-view:testDebugUnitTest
./gradlew :app:lintStandardDebug
```

调试包 ID 为 `dev.lbh.remotework.debug`，不会覆盖 Moke。后续覆盖安装需使用同一签名。自行构建的 debug 包使用各自机器的调试签名，与其他人构建的包可能无法直接覆盖安装。当前为开发版本。

实际通过的验证与未完成项见 [质量与验收](docs/QUALITY.md)。小米真机手感需要在手机上验证，不能由 JVM 测试替代。

## 来源、许可与反馈

本仓库通过 GitHub fork 保留与 Moke 的派生关系和上游提交历史。Moke 与本分支 Android 产品代码使用 **GPL-3.0-or-later**；Termux 终端组件保留 **Apache-2.0**，独立安装的 zmx 使用 **MIT**。详见 [LICENSE](LICENSE)、[版权说明](COPYRIGHT.md)、[第三方许可](THIRD_PARTY_NOTICES.md) 和 [上游及修改范围](docs/UPSTREAM.md)。

本分支的问题请提交到 [本仓库 Issues](https://github.com/Komorebi-G/resume-terminal/issues)。本分支改动由本仓库维护，不代表上游作者的观点、背书或支持承诺。
