# 0.1.5 日常基线与探索归档

2026-10-02 用户停止同步终端、字符格子接力和原生窗口远控探索。日常使用恢复到未加入同步终端的 0.1.5；后续不自动重启这些实验。

## 日常版本

- 日常项目：`~/projects/juicessh`，分支 `resume/0.1.5-rollback`。
- 原始 0.1.5：`5cd8341`，标签 `resume-v0.1.5`；只读对照工作树 `~/projects/juicessh-worktrees/0.1.5`。
- 可覆盖安装的回退包：标签 `resume-v0.1.5-rollback` / 提交 `43290b2`。相对原始 0.1.5 只将 Android versionCode 从 6 改为 9，versionName 与功能仍为 0.1.5；日常分支后续只追加本归档说明。
- 安装包：`~/Desktop/续端-0.1.5-回退版.apk`，包名 `dev.lbh.remotework.debug`，SHA256 `3a0fc3c8b963095b478c5d9542e0383d801483a9996ac3c84d2f323b2048c4ee`。

电脑继续使用普通 cmd / PowerShell / Windows Terminal / WSL；无需执行实验启动器。0.1.5 原有的手机 SSH、编辑器和手机持久终端功能保留，`remote-work` 仍是用户主动调用的可选工具，没有电脑自动接管 hook。

当前 ADB 只连接测试模拟器，不能替用户确认手机已安装回退包。手机安装上述 APK 后使用原“续端”入口；独立接力实验 App 的安装不会替换原包。

## 保留的探索

统一目录：`~/archive/projects/juicessh-exploration-2026-10-02/`。保留 Git 分支、完整历史 bundle、源码快照、关键 APK、薄 JNI 二进制、原始测试证据和说明。实验工作树撤出日常项目目录，编译缓存可从源码重新生成。

| 历史方向 | Git 分支 | 最终提交 | 状态 |
| --- | --- | --- | --- |
| 0.1.6–0.1.7 自动持久化/同步 | `archive/resume-0.1.7` | `ed87338` | 已撤回，保留原快照 |
| zmx 单端接力 | `archive/terminal-handoff-2026-10-02` | `b9a249b` | 滚动、显示与体验未满足需求 |
| 后台字符格子接力 | `archive/native-relay-2026-10-02` | `1af1e81` | 0.3.0 实验包撤回，交还等未验收 |
| 成熟原生窗口远控 | `archive/native-session-2026-10-02` | `c1e1d89` | 部分 Android 连接/视图检查通过；真实 Codex 滚动失败，输入、所有权和交还未验收 |

最后一项还保留未完成的区域接口及测试失败记录，避免将“代码能构建”和“连接能显示”误当作使用体验合格。原始日志和个人桌面截图仅在本机私有归档内，不提交 Git。

## 查阅与恢复工作树

```bash
cd ~/projects/juicessh
git worktree list
git branch --list 'resume/*' 'archive/*'
git show archive/native-session-2026-10-02:docs/NATIVE-SESSION.md

# 按需重建某个历史工作树，不改变日常项目：
git worktree add ~/projects/juicessh-worktrees/native-session archive/native-session-2026-10-02
# 原始 0.1.5 的已有对照工作树：
git -C ~/projects/juicessh-worktrees/0.1.5 log -1 --oneline
```

如本地仓库丢失，可从归档中的 `repository.bundle` 克隆，再按上述分支重建工作树。归档根目录 `README.md`、`manifest.json` 和 `SHA256SUMS` 记录内容、校验与最终环境检查。
