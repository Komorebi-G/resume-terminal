#!/usr/bin/env python3
# Added for Resume Terminal, 2026-09-30 to 2026-10-01.
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run Android installer tests through a temporary authenticated SSH server.

Requires paramiko in this Python environment and the app/test APKs on the emulator.
No SSH system configuration or saved app credentials are changed.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import threading

import paramiko

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--adb-port", default="5038")
parser.add_argument("--host", default="10.0.2.2", help="Emulator address of this host")
parser.add_argument("--all", action="store_true", help="Include terminal/IME/panel regressions")
parser.add_argument("--output", required=True)
args = parser.parse_args()

key = paramiko.RSAKey.generate(2048)
password = secrets.token_hex(24)
fingerprint = "SHA256:" + base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip("=")
stop = threading.Event()
transports = []
workers = []
channels = []  # Paramiko keeps weak channel references; retain accepted channels until cleanup.

with tempfile.TemporaryDirectory(prefix="resume-ssh-install-") as home:
    env = dict(os.environ, HOME=home, PATH="/usr/bin:/bin", SHELL="/bin/sh")
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen(8)
    listener.settimeout(0.5)
    port = listener.getsockname()[1]

    def execute(channel, command):
        lost_reply = command.startswith("RESUME_TEST_DROP_REPLY=1;")
        try:
            result = subprocess.run(["/bin/sh", "-c", command], env=env,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=115)
            if not lost_reply and not channel.closed:
                channel.sendall(result.stdout)
                channel.sendall_stderr(result.stderr)
                channel.send_exit_status(result.returncode)
        except (OSError, EOFError, paramiko.SSHException):
            if not channel.closed:
                raise
        finally:
            channel.close()

    class Server(paramiko.ServerInterface):
        def check_auth_password(self, username, candidate):
            valid = username == "fixture" and secrets.compare_digest(candidate, password)
            return paramiko.AUTH_SUCCESSFUL if valid else paramiko.AUTH_FAILED

        def get_allowed_auths(self, username):
            return "password"

        def check_channel_request(self, kind, channel_id):
            return paramiko.OPEN_SUCCEEDED if kind == "session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

        def check_channel_exec_request(self, channel, command):
            worker = threading.Thread(target=execute, args=(channel, command.decode()), daemon=True)
            workers.append(worker)
            worker.start()
            return True

    def serve_connection(connection):
        transport = paramiko.Transport(connection)
        transports.append(transport)
        transport.add_server_key(key)
        try:
            transport.start_server(server=Server())
            while transport.is_active() and not stop.is_set():
                channel = transport.accept(0.5)
                if channel is not None:
                    channels.append(channel)
        except (paramiko.SSHException, EOFError):
            pass
        finally:
            transport.close()

    def accept_connections():
        while not stop.is_set():
            try:
                connection, _ = listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            threading.Thread(target=serve_connection, args=(connection,), daemon=True).start()

    server_thread = threading.Thread(target=accept_connections, daemon=True)
    server_thread.start()
    # Check the fixture itself before waiting on the slower Android test runner.
    with socket.create_connection(("127.0.0.1", port), timeout=10) as connection:
        probe = paramiko.Transport(connection)
        try:
            probe.start_client(timeout=10)
            assert probe.get_remote_server_key() == key
            probe.auth_password("fixture", password)
            channel = probe.open_session(timeout=10)
            channel.exec_command("printf '__FIXTURE_READY__'")
            assert channel.makefile("rb").read() == b"__FIXTURE_READY__"
        finally:
            probe.close()
    classes = ["com.briqt.moke.ZmxSshIntegrationTest"]
    if args.all:
        classes += ["com.briqt.moke.CommandEditingTest", "com.briqt.moke.DraftInputConnectionTest",
                       "com.briqt.moke.ZmxSetupTest"]
    command = [args.adb, "-P", args.adb_port, "shell", "am", "instrument", "-w", "-r",
               "-e", "class", ",".join(classes), "-e", "companion_fixture_host", args.host,
               "-e", "companion_fixture_port", str(port), "-e", "companion_fixture_password", password,
               "-e", "companion_fixture_fingerprint", fingerprint,
               "dev.lbh.remotework.debug.test/androidx.test.runner.AndroidJUnitRunner"]
    try:
        with Path(args.output).open("w") as log:
            result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, text=True, timeout=420)
        output = Path(args.output).read_text()
        assert result.returncode == 0 and "FAILURES!!!" not in output and "OK (" in output, output[-2500:]
        assert "INSTRUMENTATION_STATUS: test=installAndLostReplyRecoverAcrossRealSshChannels" in output
        assert "INSTRUMENTATION_STATUS_CODE: 0" in output
        print(json.dumps({"real_android_ssh_install": True, "lost_reply_recovery": True,
                          "channel_timeout_recovery": True,
                          "isolated_home": True, "saved_credentials_untouched": True,
                          "all_regressions": args.all}, indent=2))
    finally:
        stop.set()
        listener.close()
        for transport in transports:
            transport.close()
        for worker in workers:
            worker.join(timeout=120)
        server_thread.join(timeout=2)
