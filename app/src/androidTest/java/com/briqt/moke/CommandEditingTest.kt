/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.briqt.moke.data.*
import com.briqt.moke.terminal.*
import com.briqt.moke.ui.TerminalScreen
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalTransport
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real TerminalScreen, IME editor and transport boundary, without external SSH credentials. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class CommandEditingTest {
    @Volatile private var inputConnection: InputConnection? = null
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val captured = StringBuffer()
    private lateinit var terminalController: TerminalController
    private var navigationBacks = 0
    private fun output() = captured.toString()
    private fun field() = compose.onNode(hasSetTextAction())
    private fun key(label: String) = when (label) {
        "^V" -> compose.onNodeWithContentDescription(context.getString(R.string.key_paste)).performClick()
        "^C" -> compose.onNodeWithContentDescription(context.getString(R.string.key_interrupt)).performClick()
        context.getString(R.string.keys_show_all), context.getString(R.string.keys_show_less) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
        else -> compose.onNodeWithText(label, substring = false).performClick()
    }

    private fun openEditor() {
        lateinit var terminal: TerminalSession
        compose.setContent {
            val quick = remember { androidx.compose.runtime.mutableStateOf(QuickShortcut.SHIFT_LEFT) }
            val controller = androidx.compose.runtime.remember { TerminalController(context).also { terminalController = it } }
            val ts = androidx.compose.runtime.remember {
                val transport = object : TerminalTransport {
                    override fun start(session: TerminalSession, columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
                        val bytes = "\u001b[?2004h\u001b[38;2;40;180;240m┌ 中文命令 ┐\u001b[0m\r\n$ ".toByteArray()
                        session.processToEmulator(bytes, bytes.size)
                    }
                    override fun write(data: ByteArray, offset: Int, count: Int) { captured.append(String(data, offset, count, Charsets.UTF_8)) }
                    override fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {}
                    override fun close() {}
                }
                terminal = TerminalSession(transport, 2000, controller)
                TermSession("input-test", Host(label = "Editing test", host = "isolated"), controller, terminal, transport,
                    null, null, MutableStateFlow("Editing test"), "Editing test", MutableStateFlow(null),
                    MutableStateFlow("Editing test"), MutableStateFlow(true), MutableStateFlow(null),
                    remoteTmuxId = MutableStateFlow(null), remoteTmuxName = MutableStateFlow(null),
                    remoteZmxName = MutableStateFlow(null), startedAt = 0)
            }
            val recordInput = remember {
                PlatformTextInputInterceptor { request, nextHandler ->
                    nextHandler.startInputMethod(object : PlatformTextInputMethodRequest {
                        override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                            request.createInputConnection(outAttributes).also { inputConnection = it }
                    })
                }
            }
            InterceptPlatformTextInput(recordInput) {
                MaterialTheme {
                    TerminalScreen(ts, "", "", 14f, 1f, 0f, 0, false, "default", true,
                        KeyboardMode.IME, ScrollMode.SMART, false, false, { _, _ -> Typeface.MONOSPACE },
                        { navigationBacks++ }, {}, {}, {}, {}, {}, {}, {}, {}, { _ -> }, { _, _ -> }, { _ -> }, { _ -> }, { _ -> }, { _ -> },
                        {}, { _ -> }, { _ -> }, {}, quickShortcut = quick.value, onQuickShortcut = { quick.value = it })
                }
            }
        }
        compose.waitUntil(10_000) { terminal.emulator != null && terminal.emulator.isBracketedPasteMode }
        compose.onNodeWithContentDescription(context.getString(R.string.key_text)).performClick()
        field().assertIsDisplayed().assertIsFocused()
        key("TAB") // Confirm keyboard row remains usable before typing.
        compose.waitUntil(5_000) { output().endsWith("\t") }
        captured.setLength(0)
        compose.waitUntil(5_000) { inputConnection != null }
    }

    @Test fun completeAndExecuteWithoutClosingEditor() {
        openEditor()
        field().performTextInput("cd /home/l")
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), "command-editor.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        // Tapping output must keep the local editor/IME route active.
        val outputPoint = compose.runOnIdle {
            val view = terminalController.view!!
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            floatArrayOf(location[0] + view.width / 2f, location[1] + view.height / 2f)
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = android.os.SystemClock.uptimeMillis()
        for ((action, time) in listOf(android.view.MotionEvent.ACTION_DOWN to now, android.view.MotionEvent.ACTION_UP to now + 50)) {
            val event = android.view.MotionEvent.obtain(now, time, action, outputPoint[0], outputPoint[1], 0)
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        field().assertIsFocused()
        field().assertTextEquals("cd /home/l")
        assertEquals("", output())
        key("TAB")
        key("TAB")
        compose.runOnIdle { assertEquals(true, inputConnection!!.deleteSurroundingText(1, 0)) }
        field().performImeAction()
        compose.waitUntil(5_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~cd /home/l\u001b[201~\t\t\u007f\r", output())
        field().assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        captured.setLength(0)
        field().performTextInput("ls")
        field().performImeAction()
        compose.waitUntil(5_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~ls\u001b[201~\r", output())
    }

    @Test fun codexPickerKeepsDraftAndInterruptDoesNotPasteIt() {
        openEditor()
        field().performTextInput("中文问题😀")
        key("⇧←")
        compose.waitUntil(5_000) { output().endsWith("D") }
        assertEquals("\u001b[1;2D", output())
        key("ALT")
        key("↑")
        compose.waitUntil(5_000) { output().endsWith("A") }
        field().assertTextEquals("中文问题😀")
        key("SHIFT")
        key("TAB")
        compose.waitUntil(5_000) { output().endsWith("Z") }
        assertEquals("\u001b[1;2D\u001b[1;3A\u001b[Z", output())
        key("^C")
        compose.waitUntil(5_000) { output().endsWith("\u0003") }
        field().assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals("\u001b[1;2D\u001b[1;3A\u001b[Z\u0003", output())
    }

    @Test fun stickyCtrlAndMultilinePromptReachActualTransport() {
        openEditor()
        field().performTextInput("unsent draft")
        key("CTRL")
        field().performTextInput("c")
        compose.waitUntil(5_000) { output().endsWith("\u0003") }
        assertEquals("\u0003", output())
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        captured.setLength(0)
        field().performTextInput("检查代码\n中文与英文")
        key("TAB")
        compose.waitUntil(5_000) { output().endsWith("\t") }
        assertEquals("\u001b[200~检查代码\r中文与英文\u001b[201~\t", output())
        field().assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
    @Test fun batchedChinesePreeditDeletionDoesNotBackspaceRemoteCommand() {
        openEditor()
        compose.runOnIdle {
            inputConnection!!.beginBatchEdit()
            inputConnection!!.setComposingText("中文候选", 1)
            inputConnection!!.deleteSurroundingText(1, 0)
            inputConnection!!.endBatchEdit()
        }
        field().assertTextEquals("中文候")
        assertEquals("", output())
        compose.runOnIdle { inputConnection!!.finishComposingText() }
        field().performImeAction()
        compose.waitUntil(5_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~中文候\u001b[201~\r", output())
    }

    @Test fun imeCommitThenSendInSameFrameKeepsTheLastCharacter() {
        openEditor()
        val text = "echo pasted-last-character中文"
        compose.runOnIdle {
            inputConnection!!.commitText(text, 1)
            inputConnection!!.performEditorAction(EditorInfo.IME_ACTION_SEND)
        }
        compose.waitUntil(5_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~" + text + "\u001b[201~\r", output())
        field().assertIsFocused()
    }

    /** Only run explicitly while an ADB operator taps the actual vendor keyboard. */
    @Test fun physicalKeyboardChineseCandidateReachesTransport() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("physicalImeProbe") == "true")
        openEditor()
        val status = android.os.Bundle().apply { putString("stream", "PHYSICAL_IME_READY\n") }
        InstrumentationRegistry.getInstrumentation().sendStatus(1, status)
        compose.waitUntil(90_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~你好\u001b[201~\r", output())
        saveScreenshot("physical-ime-sent.png")
    }

    private fun saveScreenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun moreKeysAndModifiedEnterAreReachable() {
        openEditor()
        compose.onNodeWithContentDescription(context.getString(R.string.key_more)).performClick()
        compose.waitForIdle()
        saveScreenshot("quick-key-panel.png")
        compose.onNodeWithText("HOME").performScrollTo().performClick()
        key("SHIFT")
        compose.onNodeWithText("Enter").performScrollTo().performClick()
        key("CTRL")
        key(context.getString(R.string.keys_show_all))
        compose.onNodeWithText("F12").performScrollTo().performClick()
        compose.waitUntil(5_000) { output().endsWith("~") }
        assertEquals("\u001b[H\u001b[13;2u\u001b[24;5~", output())
    }

    @Test fun pinnedCommandsPreserveDraftUntilExplicitSubmission() {
        openEditor()
        field().performTextInput("保留的问题😀")
        compose.onNodeWithContentDescription(context.getString(R.string.key_more)).performClick()
        key(context.getString(R.string.keys_show_all))
        compose.onNodeWithText(context.getString(R.string.quick_shortcut_configure, "Shift+←")).performScrollTo().performClick()
        compose.waitForIdle()
        saveScreenshot("quick-key-chooser.png")
        compose.onNodeWithText("/model").performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.key_more)).performClick()
        key("MODEL")
        field().assertTextEquals("/model")
        assertEquals("", output())
        key(context.getString(R.string.command_restore_draft))
        field().assertTextEquals("保留的问题😀")
        key("MODEL")
        field().performImeAction()
        compose.waitUntil(5_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~/model\u001b[201~\r", output())
        key(context.getString(R.string.command_restore_draft))
        field().assertTextEquals("保留的问题😀")
        captured.setLength(0)
        compose.onNodeWithContentDescription(context.getString(R.string.key_more)).performClick()
        key(context.getString(R.string.codex_resume_command))
        field().assertTextEquals("/resume")
        assertEquals("", output())
        field().performImeAction()
        compose.waitUntil(5_000) { output().endsWith("\r") }
        assertEquals("\u001b[200~/resume\u001b[201~\r", output())
        key(context.getString(R.string.command_restore_draft))
        field().assertTextEquals("保留的问题😀")
    }

    private fun clipboard(text: String) {
        compose.runOnIdle {
            val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            manager.setPrimaryClip(android.content.ClipData.newPlainText("test", text))
        }
    }

    @Test fun pasteReplacesDraftSelectionAndDoesNotSendOrInterrupt() {
        openEditor()
        field().performTextInput("echo old tail")
        field().performTextInputSelection(androidx.compose.ui.text.TextRange(5, 8))
        clipboard("新🙂\u0003")
        key("CTRL")
        key("^V")
        field().assertTextEquals("echo 新🙂 tail")
        assertEquals("", output())
        field().performTextInput("x") // Sticky Ctrl was cleared by paste.
        field().assertTextEquals("echo 新🙂x tail")
        assertEquals("", output())
    }

    @Test fun directSingleLinePasteAndMultilineReview() {
        openEditor()
        compose.onNodeWithContentDescription(context.getString(R.string.action_close)).performClick()
        clipboard("echo 中文\u0003")
        key("^V")
        compose.waitUntil(5_000) { output().isNotEmpty() }
        assertEquals("\u001b[200~echo 中文\u001b[201~", output())
        captured.setLength(0)
        clipboard("echo one\necho two\n")
        key("^V")
        field().assertTextEquals("echo one\necho two\n")
        assertEquals("", output())
        field().assertIsFocused()
        saveScreenshot("clipboard-multiline-review.png")
    }

    @Test fun hardwareClipboardShortcutsStayInDraftAndCtrlCCopiesSelection() {
        openEditor()
        field().performTextInput("copy this")
        field().performTextInputSelection(androidx.compose.ui.text.TextRange(0, 4))
        field().performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.CtrlLeft)
            pressKey(androidx.compose.ui.input.key.Key.C)
            keyUp(androidx.compose.ui.input.key.Key.CtrlLeft)
        }
        compose.runOnIdle { assertEquals("copy", terminalController.clipboardText()) }
        assertEquals("", output())
        clipboard("粘贴")
        field().performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.CtrlLeft)
            pressKey(androidx.compose.ui.input.key.Key.V)
            keyUp(androidx.compose.ui.input.key.Key.CtrlLeft)
        }
        field().assertTextEquals("粘贴 this")
        assertEquals("", output())
    }

    @Test fun hardwareSelectAllAndCopyOperateOnLocalDraft() {
        openEditor()
        field().performTextInput("local draft 中文")
        field().performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.CtrlLeft)
            pressKey(androidx.compose.ui.input.key.Key.A)
            pressKey(androidx.compose.ui.input.key.Key.C)
            keyUp(androidx.compose.ui.input.key.Key.CtrlLeft)
        }
        compose.runOnIdle { assertEquals("local draft 中文", terminalController.clipboardText()) }
        field().assertTextEquals("local draft 中文")
        assertEquals("", output())
    }

    @Test fun outputSelectionWhileDraftOpenTurnsInterruptIntoCopy() {
        openEditor()
        field().performTextInput("保留草稿")
        var selected = ""
        compose.runOnIdle {
            val view = terminalController.view!!
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 24f, 12f, 0)
            view.startTextSelectionMode(event)
            event.recycle()
            assertEquals(true, view.isSelectingText)
            selected = view.selectedText
            org.junit.Assert.assertTrue(selected.isNotEmpty())
        }
        compose.waitForIdle()
        saveScreenshot("clipboard-selection-before-copy.png")
        compose.runOnIdle { assertEquals("Selection should survive recomposition", true, terminalController.view!!.isSelectingText) }
        key(context.getString(R.string.copy_text))
        compose.runOnIdle { assertEquals(selected, terminalController.clipboardText()) }
        field().assertTextEquals("保留草稿")
        assertEquals("", output())
        compose.onNodeWithContentDescription(context.getString(R.string.key_interrupt)).assertExists()
        saveScreenshot("clipboard-copy-keeps-draft.png")
    }

    @Test fun heldHardwareCopyOfOutputWithEditorFocusedNeverInterrupts() {
        openEditor()
        field().performTextInput("keep draft")
        var selected = ""
        compose.runOnIdle {
            val view = terminalController.view!!
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 24f, 12f, 0)
            view.startTextSelectionMode(event)
            event.recycle()
            selected = view.selectedText
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val root = terminalController.view!!.rootView
            root.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_C, 0, android.view.KeyEvent.META_CTRL_ON))
        }
        compose.waitForIdle() // The selection has gone, but C is still held.
        compose.runOnIdle {
            val root = terminalController.view!!.rootView
            root.dispatchKeyEvent(android.view.KeyEvent(0, 500, android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_C, 1, android.view.KeyEvent.META_CTRL_ON))
            root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_C))
            assertEquals(selected, terminalController.clipboardText())
        }
        assertEquals("", output())
        field().assertTextEquals("keep draft")
    }

    @Test fun modifierTapCancelsAndLongPressLocksUntilExplicitlyCancelled() {
        openEditor()
        key("CTRL")
        key("CTRL")
        field().performTextInput("c")
        field().assertTextEquals("c")
        assertEquals("", output())
        field().performTextClearance()
        compose.onNodeWithText("CTRL").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(true, terminalController.ctrlLocked) }
        saveScreenshot("simple-modifier-locked.png")
        key("←")
        key("←")
        compose.waitUntil(5_000) { output() == "\u001b[1;5D\u001b[1;5D" }
        key("CTRL")
        compose.runOnIdle { assertEquals(false, terminalController.ctrlActive) }
        key("←")
        compose.waitUntil(5_000) { output().endsWith("\u001b[D") }
        assertEquals("\u001b[1;5D\u001b[1;5D\u001b[D", output())
    }

    @Test fun compactPanelKeepsAdvancedKeysReachableAndBackPreservesDraft() {
        openEditor()
        field().performTextInput("keep draft")
        compose.onNodeWithContentDescription(context.getString(R.string.key_more)).performClick()
        compose.onNodeWithText("F12").assertDoesNotExist()
        compose.onNodeWithText("^D").assertDoesNotExist()
        saveScreenshot("simple-common-keys.png")
        key(context.getString(R.string.keys_show_all))
        compose.onNodeWithText("F12").performScrollTo().assertIsDisplayed()
        saveScreenshot("simple-all-keys.png")
        key("CTRL")
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.runOnIdle { assertEquals(false, terminalController.ctrlActive) }
        compose.onNodeWithText("F12").assertDoesNotExist()
        field().assertTextEquals("keep draft")
        assertEquals(0, navigationBacks)
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        field().assertDoesNotExist()
        assertEquals(0, navigationBacks)
        compose.onNodeWithContentDescription(context.getString(R.string.key_text)).performClick()
        field().assertTextEquals("keep draft")
        compose.onNodeWithContentDescription(context.getString(R.string.key_more)).performClick()
        compose.onNodeWithText("F12").assertDoesNotExist() // Compact each time the panel opens.
        assertEquals("", output())
    }
}
