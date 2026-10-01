/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke

import androidx.test.platform.app.InstrumentationRegistry
import com.briqt.moke.data.Host
import com.briqt.moke.terminal.*
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.PublicKey

/** Real SSH exec + APK assets + an isolated Linux home; fixtures never enter saved app connections. */
class ZmxSshIntegrationTest {
    @Test fun installAndLostReplyRecoverAcrossRealSshChannels() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Run scripts/test-companion-ssh.py for the isolated SSH fixture", args.containsKey("companion_fixture_port"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hostname = args.getString("companion_fixture_host")!!
        val port = args.getString("companion_fixture_port")!!.toInt()
        val fingerprint = args.getString("companion_fixture_fingerprint")!!
        SSHClient().use { client ->
            client.connectTimeout = 10_000
            client.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean =
                    HostKeyFingerprint.of(key) == fingerprint
                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            })
            client.connect(hostname, port)
            client.authPassword("fixture", args.getString("companion_fixture_password")!!)
            val transport = SshTransport(Host(host = hostname), context)
            // Supply the authenticated fixture connection; exercise the production exec implementation.
            SshTransport::class.java.getDeclaredField("ssh").apply { isAccessible = true }.set(transport, client)
            try {
                assertEquals(ZmxDiscovery.NotInstalled, Zmx.parseDiscovery(transport.exec(Zmx.DISCOVER_CMD)!!))
                val installer = context.assets.open("companion/install.sh").bufferedReader().use { it.readText() }
                val helper = context.assets.open("companion/remote-work").bufferedReader().use { it.readText() }
                val command = ZmxInstaller.command(installer, helper)
                val installed = Zmx.parseAction(transport.exec(command, ZmxInstaller.TIMEOUT_MILLIS)!!)!!
                assertTrue(installed.output, installed.ok)
                assertEquals(ZmxDiscovery.Ready(emptyList()), Zmx.parseDiscovery(transport.exec(Zmx.DISCOVER_CMD)!!))
                // Fixture drops the result after execution, as if an SSH channel lost the reply.
                val lost = transport.exec("RESUME_TEST_DROP_REPLY=1; $command", ZmxInstaller.TIMEOUT_MILLIS)
                assertNull(lost?.let(Zmx::parseAction))
                assertEquals(ZmxDiscovery.Ready(emptyList()), Zmx.parseDiscovery(transport.exec(Zmx.DISCOVER_CMD)!!))
                val repeated = Zmx.parseAction(transport.exec(command, ZmxInstaller.TIMEOUT_MILLIS)!!)!!
                assertTrue(repeated.output, repeated.ok)
                assertNull(transport.exec("sleep 2", 1_000))
                assertEquals(ZmxDiscovery.Ready(emptyList()), Zmx.parseDiscovery(transport.exec(Zmx.DISCOVER_CMD)!!))
            } finally { transport.close() }
        }
    }
}
