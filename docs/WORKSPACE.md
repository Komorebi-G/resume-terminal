# 工作区说明

日常项目保持在 `~/projects/juicessh`，开发分支为 `main`，跟踪 GitHub 的 `origin/main`。

| 目录 | 用途 |
| --- | --- |
| `app/` | Kotlin / Compose Android 客户端、资源与测试 |
| `terminal-emulator/` | Termux 终端解析、屏幕缓冲与会话引擎 |
| `terminal-view/` | Android 终端视图、文字渲染与输入 |
| `gradle/` | Gradle Wrapper 与依赖版本配置 |
| `scripts/` | 原生构建、字体下载与验证工具 |
| `wsl/` | 旧安装路径兼容入口与持久终端测试 |
| `docs/` | 安装、质量、历史与更新说明 |
| `.github/` | CI、反馈模板与参与约定 |

三个源码模块有明确的依赖关系，不是重复项目。`local.properties` 是本机 SDK 配置，不入库；各模块的 `build/`、`.gradle/`、`.kotlin/` 和 Python `__pycache__/` 都是可重新生成的缓存。构建后可运行 `./gradlew clean` 移除模块构建产物；需要的 APK 应先复制到交付目录。

## 2026-10-05 整理

旧安装包、构建元数据、本机日志、截图和连接记录保留在 `~/archive/projects/juicessh-local-2026-10-05/`，沿用原相对目录，方便查阅旧元数据。整理前的修改备份与迁移记录在 `~/.local/state/file-organization/juicessh-cleanup-*`。上述私人文件不提交到 GitHub。

原始 0.1.5 对照工作树 `~/projects/juicessh-worktrees/0.1.5` 无未提交修改，已通过 `git worktree remove` 撤出。Git worktree 是同一仓库在另一个目录中的检出，共享提交历史，可以用于同时查看不同分支。移除干净工作树不影响它的提交、分支或标签。

本地旧上游 `main` 改名为 `archive/upstream-baseline`，当前日常分支改名为 `main`。其他历史分支与标签继续保留，详见 [ARCHIVE](ARCHIVE.md)。需要对照某个历史版本时，可临时检出：

```bash
git worktree add --detach ~/archive/projects/juicessh-0.1.5-reference resume-v0.1.5
# 查阅完成后撤出：
git worktree remove ~/archive/projects/juicessh-0.1.5-reference
```
