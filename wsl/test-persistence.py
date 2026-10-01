#!/usr/bin/env python3
# Added for Resume Terminal, 2026-09-30 to 2026-10-01.
# SPDX-License-Identifier: GPL-3.0-or-later
"""Real PTY restore, shared namespace, UTF-8 and key-byte check (no GUI needed)."""
import fcntl
import json
import os
from pathlib import Path
import pty
import select
import shutil
import struct
import subprocess
import tempfile
import termios
import time
import uuid

ROOT = Path(__file__).resolve().parent
NAME = "__resume_test_" + uuid.uuid4().hex[:12]
ENV = dict(os.environ, ZMX_DIR=str(Path.home() / ".local/state/resume-terminal/zmx"))
managed = Path.home() / ".local/share/resume-terminal/bin/zmx"
ZMX = str(managed) if managed.is_file() else shutil.which("zmx")
if not ZMX:
    raise SystemExit("Install the Linux companion first: sh wsl/install.sh")


def client(command, env):
    master, slave = pty.openpty()
    fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack("HHHH", 24, 60, 0, 0))
    process = subprocess.Popen(command, stdin=slave, stdout=slave, stderr=slave, env=env)
    os.close(slave)
    return master, process


def receive(fd, needle, timeout=8):
    data, deadline = b"", time.monotonic() + timeout
    while time.monotonic() < deadline:
        if select.select([fd], [], [], 0.2)[0]:
            data += os.read(fd, 65536)
            if needle in data:
                return data
    raise AssertionError("PTY output missing %r; got %r" % (needle, data[-800:]))


def detach(fd, process):
    os.close(fd)
    try:
        process.wait(timeout=2)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=2)


with tempfile.TemporaryDirectory(prefix="resume-pty-") as temp:
    log = Path(temp) / "keys"
    heartbeat = Path(temp) / "heartbeat"
    program = (
        "import os,tty,select,time; tty.setraw(0); "
        "os.write(1, ('\\x1b[38;2;40;180;240m┌ 中文 Codex ┐\\x1b[0m\\r\\n"
        "SAME_PID=%s\\r\\n' % os.getpid()).encode()); "
        "f=open(" + repr(str(log)) + ",'ab',buffering=0); "
        "h=open(" + repr(str(heartbeat)) + ",'ab',buffering=0)\n"
        "while True:\n"
        " h.write(b'.')\n"
        " if select.select([0], [], [], 0.1)[0]:\n"
        "  data=os.read(0,1024)\n"
        "  if not data: break\n"
        "  f.write(data)\n"
    )
    first = second = None
    try:
        first = client([str(ROOT / "remote-work"), NAME, "python3", "-u", "-c", program],
                       dict(os.environ, XDG_RUNTIME_DIR=temp))
        original = receive(first[0], b"SAME_PID=")
        listing = subprocess.check_output([ZMX, "list"], env=ENV).decode()
        assert NAME in listing
        entry = next(line for line in listing.splitlines() if line.strip().startswith("name=" + NAME + "\t"))
        pid = entry.split("pid=", 1)[1].split("\t")[0]
        detach(*first)
        first = None
        # A running task must continue making progress with zero clients attached.
        before = heartbeat.stat().st_size
        time.sleep(0.6)
        assert heartbeat.stat().st_size >= before + 3, "task stopped while detached"
        # Mimic an SSH shell with no desktop environment. The wrapper finds the same PTY.
        ssh_env = {k: v for k, v in os.environ.items() if k not in ("XDG_RUNTIME_DIR", "TMPDIR", "ZMX_DIR")}
        second = client([str(ROOT / "remote-work"), NAME], ssh_env)
        restored = receive(second[0], b"SAME_PID=")
        assert "中文 Codex".encode() in restored, repr(restored)
        assert b"38;2;" in restored, "truecolor snapshot missing"
        assert "pid=" + pid in subprocess.check_output([ZMX, "list"], env=ENV).decode()
        keys = b"\x1b[1;3A\t" + "你好🙂".encode() + b"\x7f"
        os.write(second[0], keys)
        deadline = time.monotonic() + 5
        while (not log.exists() or len(log.read_bytes()) < len(keys)) and time.monotonic() < deadline:
            time.sleep(0.05)
        assert log.read_bytes() == keys, repr(log.read_bytes())
        print(json.dumps({"task_progress_without_any_client": True, "restored_same_process": True, "shared_desktop_ssh_namespace": True,
                          "restored_cjk_truecolor": True, "alt_up_tab_utf8_backspace": True}, indent=2))
    finally:
        for connection in (first, second):
            if connection:
                detach(*connection)
        subprocess.run([ZMX, "kill", NAME], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
