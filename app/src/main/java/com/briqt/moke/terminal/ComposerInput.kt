/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

/** A draft is local until sent. Completion/editing must receive its text before the key. */
object ComposerInput {
    data class Dispatch(val text: String = "", val key: String = "", val remainingDraft: String = "")

    fun shortcut(draft: String, bytes: String): Dispatch = when (bytes) {
        // Interrupt/cancel must never insert the unsent draft into a running task.
        "\u0003", "\u001b" -> Dispatch(key = bytes)
        // These change Codex UI/mode; keep the unsubmitted prompt in the editor.
        "\u001b[1;3A", "\u001b[1;3B", "\u001b[1;2D", "\u001b[1;2C", "\u001b[Z" ->
            Dispatch(key = bytes, remainingDraft = draft)
        "" -> Dispatch(remainingDraft = draft)
        else -> Dispatch(text = draft, key = bytes)
    }

    fun send(draft: String, enter: Boolean) = Dispatch(text = draft, key = if (enter) "\r" else "")

    /** A single ASCII insertion after explicit Ctrl/Alt activation is a shortcut, even if
     * the IME marks the letter as composing. Otherwise Ctrl+C on Gboard never fires.
     * Non-ASCII input, replacements, deletions and predictions remain local editing.
     */
    fun modifiedInsertion(before: String, after: String): String? {
        if (after.length != before.length + 1) return null
        val index = before.indices.firstOrNull { before[it] != after[it] } ?: before.length
        val inserted = after[index]
        if (inserted !in ' '..'~' || after.removeRange(index, index + 1) != before) return null
        return inserted.toString()
    }
}
