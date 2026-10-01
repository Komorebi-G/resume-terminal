#!/bin/sh
# Added for Resume Terminal, 2026-09-30 to 2026-10-01.
# SPDX-License-Identifier: GPL-3.0-or-later
# Compatibility entry point; the app and desktop share one Linux installer.
set -eu
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec sh "$repo_dir/app/src/main/assets/companion/install.sh"
