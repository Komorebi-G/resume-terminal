/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.briqt.moke.data.Host
import com.briqt.moke.terminal.*
import com.briqt.moke.ui.MokeViewModel
import com.briqt.moke.ui.ZmxPanel
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalTransport
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real panel + ViewModel + shipped asset command; only the SSH transport is isolated. */
class ZmxSetupTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = AtomicInteger()
    private val writes = AtomicInteger()
    private val gate = CountDownLatch(1)
    @Volatile private var result = "__MOKE_ZMX_RC__:0\nInstalled"
    @Volatile private var blocking = false
    private lateinit var vm: MokeViewModel
    private lateinit var ts: TermSession

    private fun showPanel() {
        vm = MokeViewModel(context.applicationContext as MokeApplication)
        val controller = TerminalController(context)
        val transport = object : TerminalTransport {
            override fun start(session: TerminalSession, columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {}
            override fun write(data: ByteArray, offset: Int, count: Int) { writes.incrementAndGet() }
            override fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {}
            override fun close() {}
            override fun exec(command: String): String = "__MOKE_ZMX__:ready\n"
            override fun exec(command: String, timeoutMillis: Long): String {
                assertEquals(120_000L, timeoutMillis)
                assertTrue(command.contains("__RESUME_INSTALL_SCRIPT_END__"))
                assertTrue(command.contains("https://zmx.sh/a/zmx-0.8.1-linux-"))
                calls.incrementAndGet()
                if (blocking) assertTrue(gate.await(15, TimeUnit.SECONDS))
                return result
            }
        }
        compose.runOnIdle {
            ts = TermSession("setup-test", Host(label = "Linux setup", host = "isolated"), controller,
                TerminalSession(transport, 2000, controller), transport, null, null,
                MutableStateFlow("Linux setup"), "Linux setup", MutableStateFlow(null),
                MutableStateFlow("Linux setup"), MutableStateFlow(true), MutableStateFlow(null),
                remoteTmuxId = MutableStateFlow(null), remoteTmuxName = MutableStateFlow(null),
                remoteZmxName = MutableStateFlow(null), startedAt = 0)
            ts.zmxState.value = ZmxUiState(phase = ZmxPhase.NOT_INSTALLED)
        }
        compose.setContent {
            val state by ts.zmxState.collectAsState()
            val alive by ts.alive.collectAsState()
            MaterialTheme {
                ZmxPanel(state, null, {}, { vm.refreshZmx(ts) }, {}, {}, { vm.installZmx(ts) }, alive)
            }
        }
        compose.onNodeWithText(context.getString(R.string.zmx_install)).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(300) // Wait for the Windows emulator compositor to present the frame.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun explicitInstallKeepsShellAndRejectsDuplicateRequests() {
        blocking = true
        showPanel()
        screenshot("setup-missing.png")
        try {
            compose.onNodeWithText(context.getString(R.string.zmx_install)).performClick()
            compose.waitUntil(5_000) { calls.get() == 1 }
            compose.onNodeWithText(context.getString(R.string.zmx_installing)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.zmx_new)).assertIsNotEnabled()
            vm.installZmx(ts)
            screenshot("setup-installing.png")
        } finally { gate.countDown() }
        compose.waitUntil(5_000) { ts.zmxState.value.phase == ZmxPhase.READY }
        compose.onNodeWithText(context.getString(R.string.zmx_new)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.zmx_install_success)).assertIsDisplayed()
        assertEquals(1, calls.get())
        assertEquals(0, writes.get())
        assertNull(ts.remoteZmxName.value)
        assertTrue(ts.alive.value)
        screenshot("setup-ready.png")
    }

    @Test fun failureExplainsMissingToolAndRetrySucceeds() {
        result = "__MOKE_ZMX_RC__:1\n__RESUME_INSTALL_ERROR__:dependency\nMissing command: curl"
        showPanel()
        compose.onNodeWithText(context.getString(R.string.zmx_install)).performClick()
        compose.waitUntil(5_000) { ts.zmxState.value.message != null && !ts.zmxState.value.busy }
        compose.onNodeWithText("Missing command: curl", substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.zmx_keep_plain_shell)).performScrollTo().assertIsDisplayed()
        screenshot("setup-retry.png")
        result = "__MOKE_ZMX_RC__:0\nInstalled"
        compose.onNodeWithText(context.getString(R.string.zmx_install)).performScrollTo().performClick()
        compose.waitUntil(5_000) { ts.zmxState.value.phase == ZmxPhase.READY }
        assertNull(ts.zmxState.value.message)
        assertEquals(2, calls.get())
        assertEquals(0, writes.get())
    }

    @Test fun disconnectedShellCannotStartSetup() {
        showPanel()
        compose.runOnIdle { (ts.alive as MutableStateFlow<Boolean>).value = false }
        compose.onNodeWithText(context.getString(R.string.zmx_install)).assertIsNotEnabled()
        vm.installZmx(ts)
        compose.waitForIdle()
        assertEquals(0, calls.get())
    }

    @Test fun startupFallbackClearsPersistentIdentityThroughRealTerminalEngine() {
        showPanel()
        compose.runOnIdle {
            ts.remoteZmxName.value = "saved"
            ts.controller.onZmxFallback = { ts.remoteZmxName.value = null }
            ts.session.updateSize(80, 24, 8, 16)
            val bytes = "\u001b]0;${Zmx.FALLBACK_TITLE}\u0007".toByteArray()
            ts.session.processToEmulator(bytes, bytes.size)
        }
        compose.waitUntil(5_000) { ts.remoteZmxName.value == null }
        assertTrue(ts.alive.value)
    }
}
