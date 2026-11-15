package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class PptxGeneratorTest {

    @Test
    fun buildPptxBytes_producesValidOpenXmlPackageWithAllSlides() {
        val deck = GeneratedDeck(
            deckTitle = "AI & Quantum Computing <2026>",
            subtitle = "Research Overview & Market Trends",
            accentColorHex = "10B981",
            slides = listOf(
                PresentationSlide(
                    title = "Qubits & Error Correction",
                    bullets = listOf(
                        "Logical qubits reduce error rates by >10x",
                        "Cryogenic control chips simplify scaling"
                    ),
                    keyTakeaway = "Fault-tolerant milestones are accelerating.",
                    speakerNotes = "Explain surface code basics."
                ),
                PresentationSlide(
                    title = "Enterprise Adoption & ROI",
                    bullets = listOf(
                        "Materials discovery & battery chemistry",
                        "Logistics routing & portfolio optimization"
                    ),
                    keyTakeaway = null,
                    speakerNotes = null
                )
            )
        )

        val bytes = PptxGenerator.buildPptxBytes(deck)
        assertTrue(bytes.isNotEmpty())

        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val requiredEntries = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "ppt/presentation.xml",
            "ppt/_rels/presentation.xml.rels",
            "ppt/theme/theme1.xml",
            "ppt/slideMasters/slideMaster1.xml",
            "ppt/slideLayouts/slideLayout1.xml",
            "ppt/slides/slide1.xml",
            "ppt/slides/slide2.xml",
            "ppt/slides/slide3.xml"
        )
        requiredEntries.forEach { path ->
            assertTrue("Missing OOXML entry $path", entries.containsKey(path))
        }

        // Every XML and RELS file must parse cleanly as well-formed XML
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val builder = factory.newDocumentBuilder()
        entries.forEach { (name, xml) ->
            assertTrue("$name should start with <?xml", xml.startsWith("<?xml "))
            try {
                builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            } catch (e: Exception) {
                throw AssertionError("Invalid XML in $name: ${e.message}", e)
            }
        }

        // Verify escaped characters appear cleanly in slide XML
        val slide1 = entries.getValue("ppt/slides/slide1.xml")
        assertTrue(slide1.contains("AI &amp; Quantum Computing &lt;2026&gt;"))

        val slide2 = entries.getValue("ppt/slides/slide2.xml")
        assertTrue(slide2.contains("Qubits &amp; Error Correction"))
        assertTrue(slide2.contains("Logical qubits reduce error rates by &gt;10x"))
        assertTrue(slide2.contains("Fault-tolerant milestones are accelerating."))
        assertEquals(3, entries.keys.count { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") })
    }
}
