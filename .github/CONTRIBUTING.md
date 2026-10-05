# 参与续端

本项目是 Moke 的个人使用习惯特化分支。问题和建议请提交到[本仓库 Issues](https://github.com/Komorebi-G/resume-terminal/issues)，并附设备、Android 版本、主机环境和复现步骤。

构建使用 JDK 17 与 Android SDK 35；项目根的 `local.properties` 配置 `sdk.dir`。日常开发分支为 `main`，验证命令见 [README](../README.md#构建)。

修改终端组件时保留 Apache-2.0 版权与许可，并验证相关输入或渲染行为。Android 产品代码使用 GPL-3.0-or-later。提交采用 Conventional Commits，Kotlin 遵循官方代码风格。

分发新 APK 前递增 `versionCode`、更新 [CHANGELOG](../docs/CHANGELOG.md)，运行对应单元测试、standard debug 构建和 lint。当前调试包为 `dev.lbh.remotework.debug`，覆盖安装需要相同签名。本分支没有沿用上游自动发布或自动关闭 PR 的策略。

原始 Moke 参与指南存放在 [docs/upstream](../docs/upstream/README.md)，仅作历史参考。
