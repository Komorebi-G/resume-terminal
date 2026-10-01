#!/bin/sh
# Added for Resume Terminal, 2026-09-30 to 2026-10-01.
# SPDX-License-Identifier: GPL-3.0-or-later
# User-local Linux installer. No sudo, shell profile edits or SSH/network changes.
set -eu
umask 077
companion_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
fail() {
    printf '__RESUME_INSTALL_ERROR__:%s\n%s\n' "$1" "$2" >&2
    exit 1
}
[ "$(uname -s)" = Linux ] || fail system 'Automatic setup supports Linux only.'
case "$(uname -m)" in
    x86_64) arch=x86_64; checksum=dfd75720b942466f28870731cc86dbc07afa72fb8f3bd5eeb4ff707e4eecebe8 ;;
    aarch64|arm64) arch=aarch64; checksum=943eb44c812333fd450da12097521afd3339436e86f8c2ac618b905c4c9ece68 ;;
    *) fail architecture 'Automatic setup supports Linux x86_64 and aarch64. Install zmx manually for other architectures.' ;;
esac
for tool in mkdir mktemp rm chmod mv cp grep cat; do
    command -v "$tool" >/dev/null 2>&1 || fail dependency "Missing command: $tool"
done
[ -n "${HOME:-}" ] && [ -d "$HOME" ] && [ -w "$HOME" ] || fail write 'The SSH account needs a writable home directory.'
bin_dir="$HOME/.local/bin"
managed_dir="$HOME/.local/share/resume-terminal/bin"
state_dir="$HOME/.local/state/resume-terminal/zmx"
wrapper="$bin_dir/remote-work"
if [ -e "$wrapper" ] || [ -L "$wrapper" ]; then
    [ ! -L "$wrapper" ] && grep -Fq '.local/state/resume-terminal/zmx' "$wrapper" ||
        fail conflict 'An unrelated ~/.local/bin/remote-work already exists. Rename it or install the companion manually.'
fi
zmx_bin="$managed_dir/zmx"
if [ ! -x "$zmx_bin" ]; then
    zmx_bin=$(command -v zmx 2>/dev/null || true)
    [ -n "$zmx_bin" ] || zmx_bin="$bin_dir/zmx"
fi
download=yes
if [ -x "$zmx_bin" ] && "$zmx_bin" version >/dev/null 2>&1; then
    download=no
fi
if [ "$download" = yes ]; then
    for tool in curl tar gzip; do
        command -v "$tool" >/dev/null 2>&1 || fail dependency "Missing command: $tool"
    done
    if command -v sha256sum >/dev/null 2>&1; then hash_tool=sha256sum
    elif command -v shasum >/dev/null 2>&1; then hash_tool=shasum
    else fail dependency 'Missing SHA-256 tool: sha256sum or shasum'
    fi
fi
mkdir -p "$bin_dir" "$managed_dir" "$state_dir" || fail write 'Cannot create the companion directories.'
chmod 0700 "$state_dir" || fail write 'Cannot secure the terminal state directory.'
task_tmp=$(mktemp -d "$managed_dir/.install.XXXXXXXX") || fail write 'Cannot create an installation workspace.'
trap 'rm -rf -- "$task_tmp"' EXIT
trap 'exit 1' HUP INT TERM
if [ "$download" = yes ]; then
    printf 'Downloading zmx 0.8.1 (%s)…\n' "$arch"
    curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' \
        --connect-timeout 10 --max-time 90 \
        "https://zmx.sh/a/zmx-0.8.1-linux-$arch.tar.gz" -o "$task_tmp/zmx.tar.gz" ||
        fail download 'Download failed. Check the host internet connection, DNS, proxy and CA certificates, then retry.'
    if [ "$hash_tool" = sha256sum ]; then
        actual=$(sha256sum "$task_tmp/zmx.tar.gz")
    else
        actual=$(shasum -a 256 "$task_tmp/zmx.tar.gz")
    fi
    [ "${actual%% *}" = "$checksum" ] || fail checksum 'The downloaded archive failed its SHA-256 check. Nothing was installed.'
    tar -xzf "$task_tmp/zmx.tar.gz" -C "$task_tmp" zmx || fail runtime 'Cannot extract the zmx binary.'
    chmod 0755 "$task_tmp/zmx" || fail write 'Cannot make zmx executable.'
    "$task_tmp/zmx" version || fail runtime 'zmx cannot run on this host. Check binary compatibility and noexec mount options.'
    zmx_bin="$task_tmp/zmx"
fi
cp "$companion_dir/remote-work" "$task_tmp/remote-work" || fail write 'Cannot stage the desktop helper.'
chmod 0755 "$task_tmp/remote-work" || fail write 'Cannot make the desktop helper executable.'
wrapper_tmp=$(mktemp "$bin_dir/.remote-work.XXXXXXXX") || fail write 'Cannot stage the desktop helper in ~/.local/bin.'
trap 'rm -rf -- "$task_tmp"; rm -f -- "$wrapper_tmp"' EXIT
cp "$task_tmp/remote-work" "$wrapper_tmp" && chmod 0755 "$wrapper_tmp" ||
    fail write 'Cannot stage the desktop helper.'
export ZMX_DIR="$state_dir" ZMX_NO_DETACH_KEY=1
unset ZMX_SESSION_PREFIX
"$zmx_bin" list >/dev/null || fail runtime 'Installed zmx cannot access its terminal state directory.'
# Validate both parts before publishing a new binary. Failed helper writes must not look ready.
mv -f "$wrapper_tmp" "$wrapper" || fail write 'Cannot install the desktop helper.'
if [ "$download" = yes ]; then
    mv -f "$task_tmp/zmx" "$managed_dir/zmx" || fail write 'Cannot install zmx.'
fi
printf 'Installed. Desktop command: ~/.local/bin/remote-work project\n'
