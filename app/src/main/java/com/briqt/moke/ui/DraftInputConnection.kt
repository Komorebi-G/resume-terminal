/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.ui

import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import com.briqt.moke.terminal.KeyId

/** A completion is edited remotely after the local draft has been flushed.
 * Keep candidate/local deletion in the IME; route deletion of an empty draft to the PTY.
 */
internal class DraftInputConnection(
    target: InputConnection,
    private val draftEmpty: () -> Boolean,
    private val remoteKey: (KeyId) -> Unit,
) : InputConnectionWrapper(target, false) {
    private var batchDepth = 0
    private var pendingLocalText = false
    override fun beginBatchEdit(): Boolean {
        batchDepth++
        return super.beginBatchEdit()
    }
    override fun endBatchEdit(): Boolean {
        val result = super.endBatchEdit()
        batchDepth = (batchDepth - 1).coerceAtLeast(0)
        if (batchDepth == 0) pendingLocalText = false
        return result
    }
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (batchDepth > 0 && !text.isNullOrEmpty()) pendingLocalText = true
        return super.setComposingText(text, newCursorPosition)
    }
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (batchDepth > 0 && !text.isNullOrEmpty()) pendingLocalText = true
        return super.commitText(text, newCursorPosition)
    }
    private fun deleteRemote(before: Int, after: Int): Boolean {
        if (pendingLocalText || !draftEmpty() || before < 0 || after < 0 || (before == 0 && after == 0)) return false
        repeat(before.coerceAtMost(1024)) { remoteKey(KeyId.Backspace) }
        repeat(after.coerceAtMost(1024)) { remoteKey(KeyId.Delete) }
        return true
    }
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        deleteRemote(beforeLength, afterLength) || super.deleteSurroundingText(beforeLength, afterLength)
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        deleteRemote(beforeLength, afterLength) || super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
}
