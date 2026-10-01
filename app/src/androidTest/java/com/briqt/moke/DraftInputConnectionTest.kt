/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke

import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.platform.app.InstrumentationRegistry
import com.briqt.moke.terminal.KeyId
import com.briqt.moke.ui.DraftInputConnection
import org.junit.Assert.*
import org.junit.Test

class DraftInputConnectionTest {
    @Test fun emptyDraftBackspaceAndForwardDeleteReachRemote() {
        val keys = mutableListOf<KeyId>()
        val base = BaseInputConnection(View(InstrumentationRegistry.getInstrumentation().targetContext), true)
        val connection = DraftInputConnection(base, { true }, keys::add)
        assertTrue(connection.deleteSurroundingText(1, 0))
        assertTrue(connection.deleteSurroundingTextInCodePoints(0, 1))
        assertEquals(listOf(KeyId.Backspace, KeyId.Delete), keys)
    }
    @Test fun committedChineseDeletionStaysInLocalEditable() {
        val keys = mutableListOf<KeyId>()
        val base = BaseInputConnection(View(InstrumentationRegistry.getInstrumentation().targetContext), true)
        base.setComposingText("中文候选", 1)
        base.finishComposingText()
        val connection = DraftInputConnection(base, { base.editable.isNullOrEmpty() }, keys::add)
        assertTrue(connection.deleteSurroundingText(1, 0))
        assertEquals("中文候", base.editable.toString())
        assertTrue(keys.isEmpty())
    }
}
