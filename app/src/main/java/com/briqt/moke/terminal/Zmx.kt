/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

/** A running PTY on the remote machine. zmx keeps it alive after SSH disconnects. */
data class ZmxSession(
    val name: String,
    val pid: Long,
    val clients: Int,
    val created: Long,
    val cwd: String,
)

enum class ZmxPhase { IDLE, CHECKING, INSTALLING, READY, NOT_INSTALLED, ERROR }

data class ZmxUiState(
    val phase: ZmxPhase = ZmxPhase.IDLE,
    val sessions: List<ZmxSession> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val notice: String? = null,
)

sealed interface ZmxDiscovery {
    data object NotInstalled : ZmxDiscovery
    data class Ready(val sessions: List<ZmxSession>) : ZmxDiscovery
    data object Malformed : ZmxDiscovery
    data class Failed(val output: String) : ZmxDiscovery
}

data class ZmxActionResult(val ok: Boolean, val output: String)

/** Commands use only the existing SSH connection; the phone never exposes a zmx socket. */
object Zmx {
    // A startup fallback reports through the existing OSC title callback, before starting the shell.
    const val FALLBACK_TITLE = "__RESUME_ZMX_TEMPORARY_SHELL__"
    private const val READY = "__MOKE_ZMX__:ready"
    private const val MISSING = "__MOKE_ZMX__:missing"
    private const val FAILED = "__MOKE_ZMX__:error"
    private const val ACTION = "__MOKE_ZMX_RC__:"

    // SSH exec uses a non-interactive shell, which often omits ~/.local/bin from PATH.
    private const val RESOLVE =
        "export ZMX_DIR=\"\$HOME/.local/state/resume-terminal/zmx\" ZMX_NO_DETACH_KEY=1; " +
        "unset ZMX_SESSION_PREFIX ZMX_SESSION; " +
        "Z=\"\$HOME/.local/share/resume-terminal/bin/zmx\"; " +
        "if [ ! -x \"\$Z\" ]; then Z=\$(command -v zmx 2>/dev/null || true); " +
        "[ -n \"\$Z\" ] || Z=\"\$HOME/.local/bin/zmx\"; fi; "

    val DISCOVER_CMD = "sh -c " + quote(RESOLVE +
        "if [ ! -x \"\$Z\" ]; then printf '$MISSING\\n'; " +
        "else O=\$(\"\$Z\" list 2>/dev/null); R=\$?; " +
        "if [ \"\$R\" = 0 ]; then printf '$READY\\n'; " +
        "else printf '$FAILED\\n'; O=\$(\"\$Z\" list 2>&1); fi; printf '%s' \"\$O\"; fi")

    fun parseDiscovery(output: String): ZmxDiscovery {
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return ZmxDiscovery.Malformed
        return when (lines.first().trim()) {
            MISSING -> ZmxDiscovery.NotInstalled
            FAILED -> ZmxDiscovery.Failed(lines.drop(1).joinToString("\n"))
            READY -> {
                val sessions = lines.drop(1).mapNotNull(::parseSession)
                if (sessions.size == lines.size - 1) ZmxDiscovery.Ready(sessions)
                else ZmxDiscovery.Malformed
            }
            else -> ZmxDiscovery.Malformed
        }
    }

    private fun parseSession(line: String): ZmxSession? {
        val fields = line.trim().split('\t').associate { field ->
            val index = field.indexOf('=')
            if (index < 1) return null
            field.substring(0, index).trim() to field.substring(index + 1)
        }
        val name = fields["name"]?.takeIf { it.isNotBlank() } ?: return null
        return ZmxSession(
            name = name,
            pid = fields["pid"]?.toLongOrNull() ?: return null,
            clients = fields["clients"]?.toIntOrNull() ?: return null,
            created = fields["created"]?.toLongOrNull() ?: return null,
            cwd = fields["cwd"].orEmpty(),
        )
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun validName(name: String): Boolean = name.isNotBlank() &&
        name.toByteArray(Charsets.UTF_8).size <= 64 && !name.startsWith('-') && name !in setOf(".", "..") &&
        name.none { it == '/' || it == '\\' || Character.isISOControl(it) }

    /** Attach by stable name. Saved sessions can require an existing PTY to avoid false restoration. */
    fun attachCommand(name: String, existingOnly: Boolean = false): String {
        val script = RESOLVE +
            "plain_shell() { printf '\\033]0;$FALLBACK_TITLE\\007'; " +
            "exec \"\${SHELL:-/bin/sh}\" -l; }; " +
            "if [ ! -x \"\$Z\" ] || ! \"\$Z\" version >/dev/null 2>&1; then " +
            "echo 'zmx is unavailable. Using a temporary shell.'; " +
            "plain_shell; fi; " +
            (if (existingOnly) {
                "if ! \"\$Z\" list --short | grep -Fqx -- \"\$1\"; then " +
                    "echo 'Saved terminal no longer exists. Choose another from the terminal list.'; " +
                    "plain_shell; fi; "
            } else "") +
            "\"\$Z\" attach \"\$1\"; R=\$?; " +
            "if [ \"\$R\" != 0 ]; then echo 'Could not attach. Using a temporary shell.'; plain_shell; fi; exit 0"
        return "sh -c ${quote(script)} sh ${quote(name)}"
    }

    fun killCommand(name: String): String = actionCommand("\"\$Z\" kill ${quote(name)}")

    private fun actionCommand(command: String): String = "sh -c " + quote(RESOLVE +
        "O=\$({ if [ -x \"\$Z\" ]; then $command; else echo 'zmx is not installed'; exit 127; fi; } 2>&1); " +
        "R=\$?; printf '$ACTION%s\\n' \"\$R\"; printf '%s' \"\$O\"")

    fun parseAction(output: String): ZmxActionResult? {
        val firstBreak = output.indexOf('\n')
        val head = (if (firstBreak < 0) output else output.substring(0, firstBreak)).trim()
        if (!head.startsWith(ACTION)) return null
        val code = head.removePrefix(ACTION).toIntOrNull() ?: return null
        val body = if (firstBreak < 0) "" else output.substring(firstBreak + 1).trim()
        return ZmxActionResult(code == 0, body)
    }
}
