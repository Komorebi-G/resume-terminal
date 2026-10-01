# 版权与许可说明

moke 的产品层代码（主要为 `app/` 模块）版权归 briqt 所有，以 **GNU General Public License v3.0 或更高版本**（GPL-3.0-or-later）发布。完整许可文本见 [LICENSE](LICENSE)。

```
Copyright (C) 2026 briqt

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

## 第三方组件

本仓库包含 vendored 的第三方组件，它们各自遵循其原始许可：

- `terminal-emulator/`、`terminal-view/` 源自 termux/termux-app，采用 **Apache License 2.0**（与 GPLv3 兼容）。全文见 [LICENSE-APACHE-2.0.txt](LICENSE-APACHE-2.0.txt)。
- mosh native 组件为 **GPLv3**，如自行构建并打包，则以独立可执行文件形式随包分发；当前标准源码构建不自动内置它。

完整第三方清单见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 续端分支修改

2026-09-30 至 2026-10-01，本分支基于 Moke 增加 zmx 持久终端管理、跨设备恢复、移动键盘调整、输入法候选编辑修复与安装文档；Android 产品层新增代码继续采用 GPL-3.0-or-later。上游原作者版权声明保留。来源与修改范围见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。

续端是按个人使用习惯特化的 Moke 派生项目，并非完全原创或上游官方版本。原有代码的版权归其原作者；本分支修改的维护者为 [Komorebi-G](https://github.com/Komorebi-G)，没有重新声明对上游代码的所有权。修改文件带有修改日期，原始许可与通知继续保留。
