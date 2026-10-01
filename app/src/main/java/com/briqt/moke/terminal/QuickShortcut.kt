/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

/** User-selected shortcut; display the actual combination rather than an app-specific action. */
enum class QuickShortcut(val label: String, val title: String, val key: KeyId, val command: String? = null) {
    SHIFT_LEFT("⇧←", "Shift+←", KeyId.Macro(KeySeq.encode(KeyId.Left, shift = true))),
    SHIFT_RIGHT("⇧→", "Shift+→", KeyId.Macro(KeySeq.encode(KeyId.Right, shift = true))),
    ALT_UP("ALT↑", "Alt+↑", KeyId.Macro(KeySeq.encode(KeyId.Up, alt = true))),
    ALT_DOWN("ALT↓", "Alt+↓", KeyId.Macro(KeySeq.encode(KeyId.Down, alt = true))),
    HOME("HOME", "Home", KeyId.Home),
    END("END", "End", KeyId.End),
    CTRL_R("^R", "Ctrl+R", KeyId.Macro("\u0012")),
    ALT_ENTER("ALT↵", "Alt+Enter", KeyId.Macro("\u001b\r")),
    SHIFT_ENTER("⇧↵", "Shift+Enter", KeyId.Macro(KeySeq.encode(KeyId.Enter, shift = true))),
    MODEL("MODEL", "/model", KeyId.Macro(""), "/model"),
    RESUME("RESUME", "/resume", KeyId.Macro(""), "/resume");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: SHIFT_LEFT
    }
}
