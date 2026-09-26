package com.briqt.moke.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLinksTest {

    // ---------- 文本层 ----------

    @Test
    fun `finds the link covering the index and nothing outside it`() {
        val text = "see https://example.com/docs for details"
        assertEquals("https://example.com/docs", TerminalLinks.urlAt(text, text.indexOf("example")))
        assertEquals("https://example.com/docs", TerminalLinks.urlAt(text, text.indexOf("https")))
        assertNull(TerminalLinks.urlAt(text, 0))
        assertNull(TerminalLinks.urlAt(text, text.indexOf("for")))
    }

    @Test
    fun `trailing punctuation is not part of the link`() {
        assertEquals("https://a.com/x", TerminalLinks.urlAt("go to https://a.com/x.", 8))
        assertEquals("https://a.com/x", TerminalLinks.urlAt("(https://a.com/x)", 3))
        // 链接内部配对的括号要保留。
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", TerminalLinks.urlAt("https://en.wikipedia.org/wiki/Foo_(bar)", 3))
        // 紧跟中文标点（全角句号、逗号）。
        assertEquals("http://localhost:5173/", TerminalLinks.urlAt("打开 http://localhost:5173/。然后", 5))
        assertEquals("https://a.com", TerminalLinks.urlAt("https://a.com，下一个", 2))
    }

    @Test
    fun `only http and https`() {
        assertNull(TerminalLinks.urlAt("ftp://a.com/file", 3))
        assertNull(TerminalLinks.urlAt("file:///etc/hosts", 3))
    }

    @Test
    fun `local addresses`() {
        assertTrue(TerminalLinks.isLocal("http://localhost:5173/"))
        assertTrue(TerminalLinks.isLocal("http://127.0.0.1:8080/api"))
        assertTrue(TerminalLinks.isLocal("http://0.0.0.0:3000"))
        assertTrue(TerminalLinks.isLocal("http://[::1]:8000/"))
        assertTrue(TerminalLinks.isLocal("HTTP://LOCALHOST"))
        assertFalse(TerminalLinks.isLocal("https://example.com/localhost"))
        assertFalse(TerminalLinks.isLocal("https://localhost.example.com/"))
    }

    // ---------- 终端层：真的 TerminalEmulator ----------

    private fun emulator(cols: Int, rows: Int, text: String): TerminalEmulator {
        val out = object : TerminalOutput() {
            override fun write(data: ByteArray, offset: Int, count: Int) {}
            override fun titleChanged(oldTitle: String?, newTitle: String?) {}
            override fun onCopyTextToClipboard(text: String?) {}
            override fun onPasteTextFromClipboard() {}
            override fun onBell() {}
            override fun onColorsChanged() {}
        }
        return TerminalEmulator(out, cols, rows, 10, 20, rows * 2, null).also {
            val b = text.toByteArray(Charsets.UTF_8)
            it.append(b, b.size)
        }
    }

    @Test
    fun `soft-wrapped link is joined back`() {
        // 20 列：链接从第 0 行第 4 列开始，折到第 1、2 行。
        val url = "https://example.com/a/very/long/path"
        val emu = emulator(20, 5, "go: $url\r\nnext")
        assertEquals(url, TerminalLinks.urlAt(emu, 6, 0))
        assertEquals(url, TerminalLinks.urlAt(emu, 3, 1))   // 点在折行后的那一段
        assertNull(TerminalLinks.urlAt(emu, 1, 3))          // 下一行的 "next"
    }

    @Test
    fun `wide CJK characters before the link do not shift the hit position`() {
        // "中文" 占 4 列：链接从第 5 列开始。按"一列一字符"算的实现会在这里错位。
        val emu = emulator(40, 3, "中文 http://localhost:5173/ ok")
        assertEquals("http://localhost:5173/", TerminalLinks.urlAt(emu, 5, 0))
        assertEquals("http://localhost:5173/", TerminalLinks.urlAt(emu, 26, 0))
        assertNull(TerminalLinks.urlAt(emu, 4, 0))   // 链接前的空格
        assertNull(TerminalLinks.urlAt(emu, 29, 0))  // 链接后的 "ok"
    }

    @Test
    fun `tapping blank space is not a hit`() {
        val emu = emulator(40, 3, "https://a.com")
        assertNull(TerminalLinks.urlAt(emu, 30, 0))
        assertNull(TerminalLinks.urlAt(emu, 0, 2))
    }
}
