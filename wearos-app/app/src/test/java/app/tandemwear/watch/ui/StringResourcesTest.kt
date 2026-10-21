package app.tandemwear.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization guard rails, checked on the source files (no Android runtime needed):
 * English (fallback) and Spanish define the same keys with the same placeholders, and the
 * UI code doesn't hardcode user-visible text.
 */
class StringResourcesTest {

    /** Module directory: Gradle runs unit tests from it (`app/`). */
    private val mainDir = File("src/main")

    private fun strings(dir: String): Map<String, String> {
        val file = File(mainDir, "res/$dir/strings.xml")
        assertTrue("missing ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private fun placeholders(text: String): List<String> =
        Regex("%\\d+\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `english and spanish define the same keys`() {
        val en = strings("values")
        val es = strings("values-es")
        assertEquals("keys missing in Spanish", emptySet<String>(), en.keys - es.keys)
        assertEquals("keys only in Spanish", emptySet<String>(), es.keys - en.keys)
    }

    @Test
    fun `translations keep the same placeholders`() {
        val en = strings("values")
        val es = strings("values-es")
        en.forEach { (key, value) ->
            assertEquals("placeholders differ for '$key'", placeholders(value), placeholders(es.getValue(key)))
        }
    }

    @Test
    fun `ui code has no hardcoded user-visible text`() {
        val uiDir = File(mainDir, "java/app/tandemwear/watch/ui")
        assertTrue("missing ${uiDir.path}", uiDir.isDirectory)
        // Text("…"), text = "…", contentDescription = "…" with at least one letter
        val literal = Regex("""(\bText\(\s*|\btext\s*=\s*|contentDescription\s*=\s*)"[^"]*\p{L}[^"]*"""")
        // Whole-file match: Text( and its string are often on different lines
        val offenders = uiDir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val source = file.readText()
            literal.findAll(source).map { match ->
                val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                "${file.name}:$line: ${match.value.replace(Regex("\\s+"), " ")}"
            }
        }.toList()
        assertTrue("Hardcoded UI text (use strings.xml):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
