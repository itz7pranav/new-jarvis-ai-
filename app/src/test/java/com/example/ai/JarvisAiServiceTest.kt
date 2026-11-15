package com.example.ai

import com.example.data.model.GeminiModels
import com.example.data.model.MemoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JarvisAiServiceTest {

    private val service = JarvisAiService()

    @Test
    fun buildSmartLocalTitle_formatsCleanlyWithoutLlmCall() {
        assertEquals("New Conversation", service.buildSmartLocalTitle("   "))
        assertEquals(
            "Explain quantum entanglement in simple terms…",
            service.buildSmartLocalTitle("  explain quantum entanglement in simple terms please  ")
        )
        assertEquals("Kotlin coroutines guide", service.buildSmartLocalTitle("kotlin coroutines guide"))
    }

    @Test
    fun extractMemoriesLocally_extractsSafeCuesAndRejectsSecrets() {
        val extracted = service.extractMemoriesLocally(
            userPrompt = "Remember that I work on Android apps. My name is Alex and I prefer Kotlin.",
            existingMemories = emptyList()
        )
        assertTrue(extracted.isNotEmpty())
        assertTrue(extracted.any { it.category == "fact" && it.content.contains("Alex") })

        val secretIgnored = service.extractMemoriesLocally(
            userPrompt = "Remember my api key is 12345 and password is abc",
            existingMemories = emptyList()
        )
        assertTrue(secretIgnored.isEmpty())

        val duplicateIgnored = service.extractMemoriesLocally(
            userPrompt = "My name is Alex",
            existingMemories = listOf(
                MemoryEntry(category = "fact", content = "User prefers to be called Alex")
            )
        )
        assertTrue(duplicateIgnored.isEmpty())
    }

    @Test
    fun normalizeFreeTierModel_mapsLegacyOrUnknownToFlash() {
        assertEquals(GeminiModels.FLASH, GeminiModels.normalizeFreeTierModel(null))
        assertEquals(GeminiModels.FLASH, GeminiModels.normalizeFreeTierModel("gemini-4-argon"))
        assertEquals(GeminiModels.FLASH_LITE, GeminiModels.normalizeFreeTierModel(GeminiModels.FLASH_LITE))
    }

    @Test
    fun parseGeneratedDeckJson_parsesSlidesThemeAndNullFields() {
        val json = """
            {
              "deckTitle": "Solar Energy 2026",
              "subtitle": "Utility-Scale Economics",
              "accentTheme": "emerald",
              "slides": [
                {
                  "title": "Cost Curve",
                  "bullets": ["Utility solar LCOE fell 18%", "LFP battery pairing expanded"],
                  "keyTakeaway": "Renewables lead new capacity additions.",
                  "speakerNotes": "null"
                }
              ]
            }
        """.trimIndent()

        val deck = service.parseGeneratedDeckJson(json, "Solar fallback")
        assertEquals("Solar Energy 2026", deck.deckTitle)
        assertEquals("Utility-Scale Economics", deck.subtitle)
        assertEquals("10B981", deck.accentColorHex)
        assertEquals(1, deck.slides.size)
        assertEquals("Cost Curve", deck.slides[0].title)
        assertEquals(2, deck.slides[0].bullets.size)
        assertEquals("Renewables lead new capacity additions.", deck.slides[0].keyTakeaway)
        assertNull(deck.slides[0].speakerNotes)
    }

    @Test
    fun explicitIntentDetectors_matchImageAndPptRequestsAccurately() {
        assertTrue(service.isExplicitImageRequest("Generate an image of a cyberpunk Tokyo alley"))
        assertTrue(service.isExplicitImageRequest("/image minimalist robot icon"))
        assertFalse(service.isExplicitImageRequest("Explain how image sensors work"))

        assertTrue(service.isExplicitPptRequest("Create a 6 slide PowerPoint presentation on renewable energy"))
        assertTrue(service.isExplicitPptRequest("/ppt Q3 product roadmap"))
        assertFalse(service.isExplicitPptRequest("What does PPT stand for?"))
    }
}
