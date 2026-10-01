/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

/** Small audited scripts travel over SSH; the remote host downloads a pinned, verified binary. */
object ZmxInstaller {
    const val TIMEOUT_MILLIS = 120_000L
    private const val ERROR = "__RESUME_INSTALL_ERROR__:"

    fun command(installer: String, wrapper: String): String {
        val installEnd = "__RESUME_INSTALL_SCRIPT_END__"
        val wrapperEnd = "__RESUME_WRAPPER_SCRIPT_END__"
        require(installer.lineSequence().none { it == installEnd })
        require(wrapper.lineSequence().none { it == wrapperEnd })
        val script = """
            |set -eu
            |umask 077
            |T=${'$'}(mktemp -d) || exit 1
            |trap 'rm -rf -- "${'$'}T"' EXIT
            |trap 'exit 1' HUP INT TERM
            |cat > "${'$'}T/install.sh" <<'$installEnd'
            |$installer
            |$installEnd
            |cat > "${'$'}T/remote-work" <<'$wrapperEnd'
            |$wrapper
            |$wrapperEnd
            |sh "${'$'}T/install.sh"
        """.trimMargin()
        val action = "O=${'$'}(sh -c ${quote(script)} 2>&1); R=${'$'}?; " +
            "printf '__MOKE_ZMX_RC__:%s\\n' \"${'$'}R\"; printf '%s' \"${'$'}O\""
        return "sh -c ${quote(action)}"
    }

    fun errorCode(output: String): String? = output.lineSequence()
        .firstOrNull { it.startsWith(ERROR) }?.removePrefix(ERROR)?.trim()

    fun details(output: String): String = output.lineSequence()
        .filterNot { it.startsWith(ERROR) }.joinToString("\n").trim().takeLast(700)

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
