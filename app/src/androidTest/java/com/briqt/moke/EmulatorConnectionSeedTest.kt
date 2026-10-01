/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke

import android.util.Base64
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.briqt.moke.data.AuthType
import com.briqt.moke.data.Host
import com.briqt.moke.data.HostStore
import com.briqt.moke.data.SessionPersistence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prepare an isolated emulator connection. Credentials are supplied at runtime, never bundled. */
class EmulatorConnectionSeedTest {
    @Test fun seedDisposableConnection() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val encodedKey = args.getString("sshTestKey")
            ?: return@runBlocking // Ordinary connected tests do not change stored connections.
        assertTrue("Use a disposable emulator for fixture data", Build.MODEL.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = Host(
            label = "WSL emulator check",
            host = args.getString("sshTestHost") ?: "10.0.2.2",
            port = args.getString("sshTestPort")?.toInt() ?: 12222,
            username = args.getString("sshTestUser") ?: error("sshTestUser is required when seeding a connection"),
            authType = AuthType.KEY,
            privateKeyPem = String(Base64.decode(encodedKey, Base64.DEFAULT), Charsets.UTF_8),
            persistence = SessionPersistence.ZMX,
            zmxSessionName = args.getString("sshTestSession") ?: "",
        )
        assertTrue(HostStore(context).save(listOf(host)))
    }
}
