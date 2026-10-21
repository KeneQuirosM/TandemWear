package app.tandemwear.watch.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

class RotatingFileLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val direct = Executor { it.run() }

    private fun log(maxBytes: Long = 100, backups: Int = 2) =
        RotatingFileLog(tmp.root, "t.log", maxBytes, backups, direct)

    @Test
    fun `appends lines in order`() {
        val log = log(maxBytes = 1_000)
        log.append("one")
        log.append("two")
        assertEquals("one\ntwo\n", tmp.root.resolve("t.log").readText())
    }

    @Test
    fun `rotates and never exceeds the size limit`() {
        val log = log(maxBytes = 100, backups = 2)
        repeat(200) { log.append("line %03d xxxxxxxxxxxxxxxx".format(it)) }
        val files = log.files()
        assertEquals(listOf("t.log.2", "t.log.1", "t.log"), files.map { it.name })
        files.forEach { assertTrue("${it.name} = ${it.length()}", it.length() <= 100) }
        assertTrue(tmp.root.listFiles()!!.sumOf { it.length() } <= 3 * 100)
        // The newest line is in the current file, the oldest kept lines in the last backup
        assertTrue(tmp.root.resolve("t.log").readText().trimEnd().endsWith("line 199 xxxxxxxxxxxxxxxx"))
    }

    @Test
    fun `export concatenates oldest first`() {
        val log = log(maxBytes = 30, backups = 3)
        (1..6).forEach { log.append("entry number $it") }
        val out = ByteArrayOutputStream()
        val bytes = log.exportTo(out)
        val lines = out.toString(Charsets.UTF_8.name()).trim().lines()
        assertEquals(out.size().toLong(), bytes)
        // Whatever was kept is contiguous and in order, ending with the newest line
        assertEquals("entry number 6", lines.last())
        val numbers = lines.map { it.removePrefix("entry number ").toInt() }
        assertEquals(numbers.sorted(), numbers)
        assertEquals((numbers.first()..6).toList(), numbers)
    }

    @Test
    fun `missing directory is created`() {
        val dir = tmp.root.resolve("diag")
        RotatingFileLog(dir, "x.log", 1_000, 1, direct).append("hello")
        assertEquals("hello\n", dir.resolve("x.log").readText())
    }

    @Test
    fun `diagnostics log mirrors to the attached file with full date`() {
        val log = log(maxBytes = 10_000)
        DiagnosticsLog.attachFile(log)
        try {
            DiagnosticsLog.log("gatt: test line")
        } finally {
            DiagnosticsLog.detachFileForTest()
            DiagnosticsLog.clear()
        }
        val text = tmp.root.resolve("t.log").readText()
        assertTrue(text, Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} [+-]\d{4} gatt: test line\n$""").matches(text))
    }
}
