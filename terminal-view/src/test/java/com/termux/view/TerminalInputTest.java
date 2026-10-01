/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.termux.view;

import android.app.Activity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TerminalTransport;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Exercise the Android IME boundary, including preedit that has not reached SSH. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalInputTest {
    private final ByteArrayOutputStream sent = new ByteArrayOutputStream();
    private InputConnection input;
    private EditorInfo editor;
    private TerminalView view;
    private String copied;
    private String pasteText = "";

    @Before public void setUp() {
        TerminalTransport transport = new TerminalTransport() {
            public void start(TerminalSession session, int cols, int rows, int width, int height) {}
            public void write(byte[] data, int offset, int count) { sent.write(data, offset, count); }
            public void updateSize(int cols, int rows, int width, int height) {}
            public void close() {}
        };
        TerminalSessionClient client = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class<?>[]{TerminalSessionClient.class},
            (proxy, method, args) -> {
                if (method.getName().equals("onCopyTextToClipboard")) copied = (String) args[1];
                if (method.getName().equals("onPasteTextFromClipboard"))
                    ((TerminalSession) args[0]).getEmulator().paste(pasteText);
                return null;
            });
        TerminalSession session = new TerminalSession(transport, 200, client);
        session.updateSize(80, 24, 8, 16);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        view = new TerminalView(activity, null);
        view.setTerminalViewClient(defaults(TerminalViewClient.class));
        view.setTextSize(16);
        view.setFocusableInTouchMode(true);
        activity.setContentView(view);
        view.attachSession(session);
        view.layout(0, 0, 800, 480);
        view.mEmulator = session.getEmulator();
        editor = new EditorInfo();
        input = view.onCreateInputConnection(editor);
    }

    private static <T> T defaults(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false :
                method.getReturnType() == float.class ? 1f : null));
    }

    private String output() { return new String(sent.toByteArray(), StandardCharsets.UTF_8); }

    @Test public void chinesePreeditBackspaceNeverDeletesRemoteCommand() {
        input.commitText("codex ", 1);
        input.setComposingText("zhongw", 1);
        input.deleteSurroundingText(1, 0);
        assertEquals("zhong", input.getTextBeforeCursor(100, 0).toString());
        assertEquals("codex ", output());
        input.commitText("中文", 1);
        assertEquals("codex 中文", output());
        input.finishComposingText();
        assertEquals("codex 中文", output());
    }

    @Test public void backspaceWithoutPreeditReachesTerminal() {
        input.commitText("abc", 1);
        input.deleteSurroundingText(1, 0);
        assertEquals("abc\u007f", output());
    }

    @Test public void emojiPreeditDeletesWholeCodePoint() {
        input.setComposingText("中🙂", 1);
        input.deleteSurroundingTextInCodePoints(1, 0);
        assertEquals("中", input.getTextBeforeCursor(100, 0).toString());
        assertEquals("", output());
        input.finishComposingText();
        assertEquals("中", output());
    }

    @Test public void enterIsCarriageReturnAndMultilingualTextIsUtf8() {
        input.commitText("echo 你好🙂\n", 1);
        assertEquals("echo 你好🙂\r", output());
    }

    @Test public void imeAllowsChineseButAvoidsCommandCorrectionsAndLearning() {
        assertEquals(InputType.TYPE_CLASS_TEXT, editor.inputType & InputType.TYPE_MASK_CLASS);
        assertTrue((editor.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
        assertTrue((editor.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
    }

    private KeyEvent down(int code, int modifiers, int repeat) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat, modifiers);
    }

    private String selectOutput(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        view.mEmulator.append(bytes, bytes.length);
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 8f, 12f, 0);
        view.startTextSelectionMode(event);
        event.recycle();
        assertTrue(view.isSelectingText());
        String selection = view.getSelectedText();
        assertNotNull(selection);
        assertFalse(selection.isEmpty());
        return selection;
    }

    @Test public void selectedCtrlCCopiesWithoutInterruptEvenWhenHeld() {
        String selected = selectOutput("中文 build failed");
        view.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON, 0));
        assertTrue(view.isSelectingText());
        view.onKeyDown(KeyEvent.KEYCODE_C, down(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON, 0));
        view.onKeyDown(KeyEvent.KEYCODE_C, down(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON, 1));
        assertEquals(selected, copied);
        assertFalse(view.isSelectingText());
        assertEquals("", output());
        view.onKeyUp(KeyEvent.KEYCODE_C, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C));
        view.onKeyDown(KeyEvent.KEYCODE_C, down(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON, 0));
        assertEquals("\u0003", output());
    }

    @Test public void ctrlShiftCWithNoSelectionNeverInterrupts() {
        view.onKeyDown(KeyEvent.KEYCODE_C, down(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, 0));
        assertNull(copied);
        assertEquals("", output());
    }

    @Test public void imeStyleControlLettersUseClipboardWithoutInterrupt() {
        String selected = selectOutput("selected text");
        input.finishComposingText(); // An empty IME finish must not cancel output selection.
        assertTrue(view.isSelectingText());
        input.commitText("\u0003", 1); // IMEs can send the actual Ctrl+C control character.
        assertEquals(selected, copied);
        assertEquals("", output());
        pasteText = "paste text";
        view.inputCodePoint(-1, 'v', true, false);
        assertEquals("paste text", output());
    }

    @Test public void bothPasteShortcutsRespectBracketedPasteAndIgnoreKeyRepeat() {
        byte[] enable = "\u001b[?2004h".getBytes(StandardCharsets.UTF_8);
        view.mEmulator.append(enable, enable.length);
        pasteText = "中文\u0003\ncommand";
        view.onKeyDown(KeyEvent.KEYCODE_V, down(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON, 0));
        view.onKeyDown(KeyEvent.KEYCODE_V, down(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON, 1));
        view.onKeyDown(KeyEvent.KEYCODE_V, down(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, 0));
        assertEquals("\u001b[200~中文\rcommand\u001b[201~\u001b[200~中文\rcommand\u001b[201~", output());
    }
}
