/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ZmxTest {
    @Test
    fun `accepts real zmx list format and an empty host`() {
        val result = Zmx.parseDiscovery(
            "__MOKE_ZMX__:ready\n" +
                "  name=codex\tpid=56462\tclients=2\tcreated=1790701054\tcwd=file://linux-host/home/developer/projects\n"
        ) as ZmxDiscovery.Ready
        assertEquals(1, result.sessions.size)
        assertEquals("codex", result.sessions.single().name)
        assertEquals(2, result.sessions.single().clients)
        assertEquals("file://linux-host/home/developer/projects", result.sessions.single().cwd)
        assertEquals(emptyList<ZmxSession>(),
            (Zmx.parseDiscovery("__MOKE_ZMX__:ready\n") as ZmxDiscovery.Ready).sessions)
    }

    @Test
    fun `distinguishes missing binary and malformed listing`() {
        assertEquals(ZmxDiscovery.NotInstalled,
            Zmx.parseDiscovery("__MOKE_ZMX__:missing\n"))
        assertEquals(ZmxDiscovery.Malformed,
            Zmx.parseDiscovery("__MOKE_ZMX__:ready\nname=x\tpid=oops\n"))
        assertEquals(ZmxDiscovery.Failed("Permission denied"),
            Zmx.parseDiscovery("__MOKE_ZMX__:error\nPermission denied"))
    }

    @Test
    fun `action result preserves failure text`() {
        assertEquals(ZmxActionResult(false, "session not found"),
            Zmx.parseAction("__MOKE_ZMX_RC__:1\nsession not found\n"))
        assertEquals(ZmxActionResult(true, ""),
            Zmx.parseAction("__MOKE_ZMX_RC__:0\n"))
    }

    @Test
    fun `names support Chinese but reject path and control characters`() {
        assertTrue(Zmx.validName("项目 Codex"))
        assertFalse(Zmx.validName("../other"))
        assertFalse(Zmx.validName("bad\nname"))
        assertFalse(Zmx.validName(" "))
        assertFalse(Zmx.validName("--force"))
        assertFalse(Zmx.validName("."))
        assertFalse(Zmx.validName("中".repeat(22)))
        assertTrue(Zmx.validName("中".repeat(21)))
    }

    @Test fun shellPreservesNamesAndSharedSocketPath() {
        withFakeZmx { home ->
            val name = "中文 ' \$(printf unsafe)"
            val output = shell(Zmx.attachCommand(name), home)
            assertTrue(output.contains("NAME=$name"))
            assertTrue(output.contains("DIR=$home/.local/state/resume-terminal/zmx"))
            assertTrue(output.contains("DETACH=1"))
            assertTrue(output.contains("SESSION=unset"))
        }
    }

    @Test fun missingRememberedSessionDoesNotCreateAnother() {
        withFakeZmx { home ->
            val output = shell(Zmx.attachCommand("missing", existingOnly = true), home)
            assertTrue(output.contains("Saved terminal no longer exists"))
            assertTrue(output.contains("FALLBACK"))
            assertTrue(output.contains(Zmx.FALLBACK_TITLE))
            assertFalse(output.contains("NAME="))
        }
    }

    @Test fun removedCompanionFallsBackToTemporaryShell() {
        withFakeZmx { home ->
            java.io.File(home, ".local/bin/zmx").delete()
            assertTrue(shell(Zmx.attachCommand("saved", existingOnly = true), home).contains("FALLBACK"))
        }
    }

    @Test fun installedBinaryFailureIsNotReportedAsEmptyReadyHost() {
        withFakeZmx { home ->
            java.io.File(home, ".local/bin/zmx").writeText("#!/bin/sh\necho permission_denied >&2\nexit 1\n")
            assertEquals(ZmxDiscovery.Failed("permission_denied"),
                Zmx.parseDiscovery(shell(Zmx.DISCOVER_CMD, home)))
            assertTrue(shell(Zmx.attachCommand("saved", existingOnly = true), home).contains("FALLBACK"))
        }
    }

    @Test fun emptyListWarningIsNotMistakenForAParseFailure() {
        withFakeZmx { home ->
            java.io.File(home, ".local/bin/zmx").writeText("#!/bin/sh\necho 'no sessions found' >&2\nexit 0\n")
            assertEquals(ZmxDiscovery.Ready(emptyList()),
                Zmx.parseDiscovery(shell(Zmx.DISCOVER_CMD, home)))
        }
    }

    @Test fun attachRuntimeFailureReportsPlainShellInsteadOfKeepingPersistentIdentity() {
        withFakeZmx { home ->
            java.io.File(home, ".local/bin/zmx").writeText("#!/bin/sh\n[ \"\$1\" = version ] && exit 0\nexit 1\n")
            val output = shell(Zmx.attachCommand("saved"), home)
            assertTrue(output.contains("FALLBACK"))
            assertTrue(output.contains(Zmx.FALLBACK_TITLE))
        }
    }

    @Test fun shellDiscoveryAndKillPreserveQuoting() {
        withFakeZmx { home ->
            val result = Zmx.parseDiscovery(shell(Zmx.DISCOVER_CMD, home)) as ZmxDiscovery.Ready
            assertEquals("saved", result.sessions.single().name)
            assertEquals(ZmxActionResult(true, "KILLED=quote'中文"),
                Zmx.parseAction(shell(Zmx.killCommand("quote'中文"), home)))
        }
    }

    private fun withFakeZmx(block: (String) -> Unit) {
        val dir = Files.createTempDirectory("resume-zmx-test").toFile()
        try {
            val bin = java.io.File(dir, ".local/bin").apply { mkdirs() }
            java.io.File(bin, "zmx").apply {
                writeText("""
                    |#!/bin/sh
                    |case "DOLLAR1" in
                    | list)
                    |   if [ "DOLLAR2" = --short ]; then echo saved
                    |   else printf 'name=saved\tpid=42\tclients=1\tcreated=123\tcwd=file:///home/u\n'; fi ;;
                    | attach) printf 'NAME=%s\nDIR=%s\nDETACH=%s\nSESSION=%s\n' "DOLLAR2" "DOLLARZMX_DIR" "DOLLARZMX_NO_DETACH_KEY" "DOLLAR{ZMX_SESSION:-unset}" ;;
                    | kill) printf 'KILLED=%s\n' "DOLLAR2" ;;
                    | version) echo 'zmx 0.8.1' ;;
                    |esac
                    |""".trimMargin().replace("DOLLAR", "$"))
                setExecutable(true)
            }
            java.io.File(bin, "test-shell").apply { writeText("#!/bin/sh\necho FALLBACK\n"); setExecutable(true) }
            block(dir.absolutePath)
        } finally { dir.deleteRecursively() }
    }

    private fun shell(command: String, home: String): String {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).apply {
            environment()["HOME"] = home
            environment()["PATH"] = "$home/.local/bin:/usr/bin:/bin"
            environment()["SHELL"] = "$home/.local/bin/test-shell"
            environment()["ZMX_SESSION"] = "unrelated-desktop-session"
        }.start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output
    }
}
