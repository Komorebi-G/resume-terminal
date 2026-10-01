/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.terminal

import org.junit.Assert.*
import org.junit.Test

class ComposerInputTest {
    @Test fun completionReceivesThePrefixExactlyOnce() {
        val first = ComposerInput.shortcut("cd /home/l", KeySeq.encode(KeyId.Tab))
        assertEquals("cd /home/l", first.text)
        assertEquals("\t", first.key)
        assertEquals("", first.remainingDraft)
        val second = ComposerInput.shortcut(first.remainingDraft, "\t")
        assertEquals("", second.text)
        assertEquals("\t", second.key)
    }
    @Test fun interruptionDoesNotTypeOrExecuteDraft() {
        for (key in listOf("\u0003", "\u001b")) {
            val action = ComposerInput.shortcut("rm example", key)
            assertEquals("", action.text)
            assertEquals(key, action.key)
            assertEquals("", action.remainingDraft)
        }
    }
    @Test fun codexModeAndQuestionPickerPreserveUnsentPrompt() {
        for (key in listOf("\u001b[1;3A", "\u001b[1;3B", "\u001b[1;2D", "\u001b[1;2C", "\u001b[Z")) {
            val action = ComposerInput.shortcut("中文问题😀", key)
            assertEquals("", action.text)
            assertEquals("中文问题😀", action.remainingDraft)
            assertEquals(key, action.key)
        }
    }
    @Test fun remoteEditingReceivesTextBeforeMovementOrDelete() {
        for (key in listOf(KeyId.Left, KeyId.Up, KeyId.Backspace, KeyId.Home, KeyId.End, KeyId.Macro("\u0017"))) {
            val bytes = KeySeq.encode(key)
            val action = ComposerInput.shortcut("中文命令", bytes)
            assertEquals("中文命令", action.text)
            assertEquals(bytes, action.key)
            assertEquals("", action.remainingDraft)
        }
    }
    @Test fun enterCanExecuteCompletedRemoteCommandWithEmptyLocalDraft() {
        assertEquals("\r", ComposerInput.send("", true).key)
        assertEquals("", ComposerInput.send("", false).key)
    }
    @Test fun multilinePromptKeepsExactTextAndSeparateSubmissionKey() {
        val prompt = "检查代码\n解释中文和😀"
        val action = ComposerInput.send(prompt, true)
        assertEquals(prompt, action.text)
        assertEquals("\r", action.key)
        assertEquals("", action.remainingDraft)
    }
    @Test fun modifierLetterCanBeInsertedAnywhereInLocalDraft() {
        assertEquals("c", ComposerInput.modifiedInsertion("ls", "lcs"))
        assertEquals("a", ComposerInput.modifiedInsertion("", "a"))
        assertEquals("w", ComposerInput.modifiedInsertion("cd ", "cd w"))
    }
    @Test fun modifierDoesNotCorruptChinesePredictionOrDeletion() {
        assertNull(ComposerInput.modifiedInsertion("", "中"))
        assertNull(ComposerInput.modifiedInsertion("", "😀"))
        assertNull(ComposerInput.modifiedInsertion("", "completion"))
        assertNull(ComposerInput.modifiedInsertion("候选", "候"))
        assertNull(ComposerInput.modifiedInsertion("候选", "汉字"))
    }
}
