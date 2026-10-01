/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Execute the shipped shell scripts in isolated homes, with controlled host tools and downloads. */
class ZmxInstallerTest {
    private val assets = File("src/main/assets/companion")

    @Test fun unsupportedHostsFailBeforeWritingAnything() = fixture {
        tool("uname", "[ \"\$1\" = -s ] && echo Darwin || echo x86_64")
        assertError("system")
        assertFalse(File(home, ".local").exists())
        tool("uname", "[ \"\$1\" = -s ] && echo Linux || echo riscv64")
        assertError("architecture")
        assertFalse(File(home, ".local").exists())
    }

    @Test fun missingDependenciesAreNamedBeforeDownload() = fixture {
        File(bin, "curl").delete()
        assertError("dependency", "curl")
        assertFalse(File(home, ".local").exists())
        File(bin, "grep").delete()
        assertError("dependency", "grep")
    }

    @Test fun unwritableHomeAndUnrelatedCommandsArePreserved() = fixture {
        val invalidHome = File(root, "not-a-directory").apply { writeText("keep") }
        assertError("write", homeOverride = invalidHome)
        assertEquals("keep", invalidHome.readText())
        val other = File(home, ".local/bin/remote-work").apply { parentFile!!.mkdirs(); writeText("unrelated") }
        assertError("conflict")
        assertEquals("unrelated", other.readText())
        assertFalse(File(home, ".local/share/resume-terminal/bin/zmx").exists())
    }

    @Test fun failedDownloadCanBeRetriedAndDoesNotLeaveStagingFiles() = fixture {
        tool("curl", "echo DNS_failure >&2; exit 6")
        assertError("download", "DNS_failure")
        assertNoStaging()
        stageArchive()
        assertTrue(run().ok)
        assertNoStaging()
    }

    @Test fun corruptedArchiveIsNeverExtractedOrInstalled() = fixture {
        stageArchive()
        tool("sha256sum", "echo bad_checksum")
        tool("tar", "echo SHOULD_NOT_EXTRACT; exit 99")
        val result = run()
        assertFalse(result.ok)
        assertEquals("checksum", ZmxInstaller.errorCode(result.output))
        assertFalse(result.output.contains("SHOULD_NOT_EXTRACT"))
        assertFalse(File(home, ".local/share/resume-terminal/bin/zmx").exists())
        assertFalse(File(home, ".local/bin/remote-work").exists())
        assertNoStaging()
    }

    @Test fun helperWriteFailureDoesNotPublishAnIncompleteBinaryInstall() = fixture {
        stageArchive()
        tool("cp", "echo Cannot_write_helper >&2; exit 1")
        assertError("write", "Cannot_write_helper")
        assertFalse(File(home, ".local/share/resume-terminal/bin/zmx").exists())
        assertFalse(File(home, ".local/bin/remote-work").exists())
        assertNoStaging()
        tool("cp", "exec /bin/cp \"\$@\"")
        assertTrue(run().ok)
    }

    @Test fun incompatibleBinaryIsRejectedBeforeInstallation() = fixture {
        stageArchive(versionFails = true)
        assertError("runtime")
        assertFalse(File(home, ".local/share/resume-terminal/bin/zmx").exists())
        assertNoStaging()
    }

    @Test fun existingZmxIsReusedOfflineAndRepeatedSetupKeepsIt() = fixture {
        val existing = File(home, ".local/bin/zmx").apply { parentFile!!.mkdirs(); writeText(fakeZmx()); setExecutable(true) }
        val before = existing.readBytes()
        File(bin, "curl").delete()
        repeat(2) { val result = run(); assertTrue(result.output, result.ok) }
        assertArrayEquals(before, existing.readBytes())
        assertFalse(File(home, ".local/share/resume-terminal/bin/zmx").exists())
        assertTrue(File(home, ".local/bin/remote-work").canExecute())
        assertEquals(setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE),
            Files.getPosixFilePermissions(File(home, ".local/state/resume-terminal/zmx").toPath()))
        assertNoStaging()
    }

    @Test fun arm64ArchiveAndSharedWrapperWorkWithQuotedHome() = fixture {
        tool("uname", "[ \"\$1\" = -s ] && echo Linux || echo aarch64")
        stageArchive(checksum = "943eb44c812333fd450da12097521afd3339436e86f8c2ac618b905c4c9ece68")
        val result = run()
        assertTrue(result.output, result.ok)
        assertTrue(File(root, "download-url").readText().contains("linux-aarch64.tar.gz"))
        val output = process(listOf("/bin/sh", File(home, ".local/bin/remote-work").path,
            "中文 ' \$(touch unsafe)", "program", "two words"))
        assertEquals("NAME=中文 ' \$(touch unsafe)\nARGS=program two words\nDIR=${home.path}/.local/state/resume-terminal/zmx\n", output)
        assertFalse(File(root, "unsafe").exists())
        assertNoStaging()
    }

    @Test fun installProtocolPreservesDetailsWithoutInternalMarkers() {
        val output = "Downloading…\n__RESUME_INSTALL_ERROR__:dependency\nMissing command: curl"
        assertEquals("dependency", ZmxInstaller.errorCode(output))
        assertEquals("Downloading…\nMissing command: curl", ZmxInstaller.details(output))
    }

    @Test fun portableWrapperRejectsUnsafeNamesAndCountsUtf8Bytes() = fixture {
        stageArchive()
        assertTrue(run().ok)
        val helper = File(home, ".local/bin/remote-work").path
        for (name in listOf("", "   ", "-bad", "../bad", "bad\\name", "bad\nname", "bad\tname", "中".repeat(22))) {
            assertFalse(process(listOf("/bin/sh", helper, name), expectedExit = 2).contains("NAME="))
        }
        assertTrue(process(listOf("/bin/sh", helper, "中".repeat(21))).contains("NAME="))
    }

    private fun fixture(block: Fixture.() -> Unit) {
        val fixture = Fixture()
        try { fixture.block() } finally { fixture.root.deleteRecursively() }
    }

    private inner class Fixture {
        val root = Files.createTempDirectory("resume-install-test").toFile()
        val home = File(root, "home ' 中文").apply { mkdirs() }
        val bin = File(root, "tools").apply { mkdirs() }
        init {
            for (name in listOf("sh", "bash", "dirname", "uname", "mkdir", "mktemp", "rm", "chmod",
                "mv", "cp", "grep", "cat", "curl", "tar", "gzip", "sha256sum")) {
                Files.createSymbolicLink(File(bin, name).toPath(), File("/usr/bin/$name").toPath())
            }
            tool("uname", "[ \"\$1\" = -s ] && echo Linux || echo x86_64")
            tool("curl", "echo DOWNLOAD_NOT_CONFIGURED >&2; exit 1")
        }

        fun tool(name: String, body: String) {
            val target = File(bin, name)
            target.delete() // Replace the symlink, never the actual host command.
            target.writeText("#!/bin/sh\n$body\n")
            target.setExecutable(true)
        }

        fun stageArchive(versionFails: Boolean = false,
            checksum: String = "dfd75720b942466f28870731cc86dbc07afa72fb8f3bd5eeb4ff707e4eecebe8") {
            val source = File(root, "zmx").apply { writeText(fakeZmx(versionFails)); setExecutable(true) }
            val archive = File(root, "archive.tar.gz")
            process(listOf("/usr/bin/tar", "-czf", archive.path, "-C", root.path, source.name))
            tool("curl", """
                |for arg do
                |  case "${'$'}arg" in https://*) printf '%s' "${'$'}arg" > '${root.path}/download-url' ;; esac
                |  target="${'$'}arg"
                |done
                |/bin/cp '${archive.path}' "${'$'}target"
            """.trimMargin())
            tool("sha256sum", "echo '$checksum  archive'")
        }

        fun run(homeOverride: File = home): ZmxActionResult {
            val command = ZmxInstaller.command(File(assets, "install.sh").readText(), File(assets, "remote-work").readText())
            return Zmx.parseAction(process(listOf("/bin/sh", "-c", command), homeOverride))!!
        }

        fun assertError(code: String, detail: String = "", homeOverride: File = home) {
            val result = run(homeOverride)
            assertFalse(result.output, result.ok)
            assertEquals(result.output, code, ZmxInstaller.errorCode(result.output))
            assertTrue(result.output, result.output.contains(detail))
        }

        fun assertNoStaging() {
            assertTrue(home.walkTopDown().none { it.name.startsWith(".install.") || it.name.startsWith(".remote-work.") })
        }

        fun process(args: List<String>, homeOverride: File = home, expectedExit: Int = 0): String {
            val p = ProcessBuilder(args).directory(root).redirectErrorStream(true).apply {
                environment()["HOME"] = homeOverride.path
                environment()["PATH"] = bin.path
            }.start()
            assertTrue("Script exceeded 10s", p.waitFor(10, TimeUnit.SECONDS))
            val output = p.inputStream.bufferedReader().readText()
            assertEquals(output, expectedExit, p.exitValue())
            return output
        }
    }

    private fun fakeZmx(versionFails: Boolean = false): String = """
        |#!/bin/sh
        |case "${'$'}1" in
        | version) ${if (versionFails) "exit 126" else "echo 'zmx 0.8.1'"} ;;
        | list) exit 0 ;;
        | attach) printf 'NAME=%s\nARGS=%s\nDIR=%s\n' "${'$'}2" "${'$'}3 ${'$'}4" "${'$'}ZMX_DIR" ;;
        |esac
    """.trimMargin()
}
